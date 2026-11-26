import fs from 'node:fs';
import path from 'node:path';
import { withTransaction } from '../db/connection.js';
import { getClientIp, HttpError, readJsonBody, readRawBody, sendJson } from '../http/io.js';
import { requireCsrf } from '../middleware/auth.js';
import { requireAdmin } from '../middleware/rbac.js';
import { revokeAllUserSessions } from '../security/session.js';
import { deleteStoredImageFile, parseMultipartSingleFile, saveValidatedImage, UploadError } from '../security/upload.js';
import {
  FOUNDER_TAGS,
  HEX_COLOR_RE,
  optionalPositiveInt,
  optionalString,
  ORDER_STATUSES,
  requireBoolean,
  requireInt,
  requireString,
  SLUG_RE,
  slugify,
  ValidationError,
} from '../security/validate.js';
import { hashIp, recordAudit } from '../utils/audit.js';

function parseIdParam(raw) {
  const id = Number.parseInt(String(raw), 10);
  if (!Number.isInteger(id) || id <= 0) throw new HttpError(400, 'ID invalido');
  return id;
}

const ACCENT_PALETTE = ['#3482FF', '#00BFA5', '#7C4DFF', '#FF6D00', '#E91E63', '#00BCD4'];

function inferFounderFromText(text) {
  const low = String(text || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
  if (low.includes('enrique')) return 'Enrique';
  if (low.includes('edgar')) return 'Edgarvis';
  if (low.includes('leonel') || low.includes('leoner')) return 'Leonel';
  if (low.includes('anibal')) return 'Aníbal';
  return null;
}

function buildSafeSlug(rawText, fallbackPrefix = 'item') {
  const base = slugify(rawText);
  if (base.length >= 2 && SLUG_RE.test(base)) return base;
  return `${fallbackPrefix}-${Date.now().toString(36)}`;
}

function resolveOrCreateCategoryId(db, { categoryId, categoryName }, now) {
  if (typeof categoryName === 'string' && categoryName.trim().length > 0) {
    const cleanName = requireString(categoryName, 'Categoria', { min: 2, max: 60 });
    const candidateSlug = buildSafeSlug(cleanName, 'cat');
    const existing = db
      .prepare('SELECT id FROM categories WHERE LOWER(name) = LOWER(?) OR slug = ? LIMIT 1')
      .get(cleanName, candidateSlug);
    if (existing) {
      db.prepare('UPDATE categories SET is_active = 1, updated_at = ? WHERE id = ?').run(now, existing.id);
      return existing.id;
    }

    let finalSlug = candidateSlug;
    if (db.prepare('SELECT id FROM categories WHERE slug = ?').get(finalSlug)) {
      finalSlug = `${candidateSlug.slice(0, 50)}-${now.toString(36).slice(-4)}`;
    }
    const founder = inferFounderFromText(cleanName);
    const accent =
      founder === 'Enrique'
        ? '#3482FF'
        : founder === 'Edgarvis'
          ? '#00BFA5'
          : founder === 'Leonel'
            ? '#7C4DFF'
            : founder === 'Aníbal'
              ? '#FF6D00'
              : ACCENT_PALETTE[Math.floor(now % ACCENT_PALETTE.length)];

    const ins = db
      .prepare(`
        INSERT INTO categories (name, slug, description, accent_hex, sort_order, is_active, created_at, updated_at)
        VALUES (?, ?, '', ?, 10, 1, ?, ?)
      `)
      .run(cleanName, finalSlug, accent, now, now);
    return Number(ins.lastInsertRowid);
  }

  return requireInt(categoryId, 'Categoria', { min: 1, max: 1_000_000 });
}

export function registerAdminRoutes(router, { db, config, limiter }) {
  // 1. Dashboard Overview
  router.get('/api/admin/dashboard', (req, res) => {
    requireAdmin(req);

    const summary = db
      .prepare(`
        SELECT
          (SELECT COALESCE(SUM(total_cents), 0) FROM orders WHERE status != 'cancelled') AS revenueCents,
          (SELECT COUNT(*) FROM orders) AS totalOrders,
          (SELECT COUNT(*) FROM orders WHERE status = 'pending') AS pendingOrders,
          (SELECT COUNT(*) FROM products WHERE is_active = 1) AS activeProducts,
          (SELECT COUNT(*) FROM products WHERE is_active = 1 AND stock <= 5) AS lowStockCount,
          (SELECT COUNT(*) FROM categories WHERE is_active = 1) AS activeCategories,
          (SELECT COUNT(*) FROM users WHERE is_active = 1) AS activeUsers,
          (SELECT COUNT(*) FROM sessions WHERE revoked_at IS NULL AND expires_at > ?) AS activeSessions
      `)
      .get(Date.now());

    const lowStockProducts = db
      .prepare(`
        SELECT id, name, slug, stock, price_cents AS priceCents
        FROM products
        WHERE is_active = 1 AND stock <= 5
        ORDER BY stock ASC, id ASC
        LIMIT 10
      `)
      .all();

    const recentAuditLogs = db
      .prepare(`
        SELECT
          al.id,
          al.action,
          al.target_type AS targetType,
          al.target_id AS targetId,
          al.metadata_json AS metadataJson,
          al.created_at AS createdAt,
          u.username AS actorUsername
        FROM audit_logs al
        LEFT JOIN users u ON u.id = al.actor_user_id
        ORDER BY al.created_at DESC, al.id DESC
        LIMIT 15
      `)
      .all();

    sendJson(res, 200, { summary, lowStockProducts, recentAuditLogs });
  });

  // 2. Categories CRUD
  router.get('/api/admin/categories', (req, res) => {
    requireAdmin(req);
    const categories = db
      .prepare(`
        SELECT
          c.id,
          c.name,
          c.slug,
          c.description,
          c.accent_hex AS accentHex,
          c.sort_order AS sortOrder,
          c.is_active AS isActive,
          COUNT(p.id) AS productCount
        FROM categories c
        LEFT JOIN products p ON p.category_id = c.id
        GROUP BY c.id
        ORDER BY c.sort_order ASC, c.id ASC
      `)
      .all();
    sendJson(res, 200, { categories });
  });

  router.post('/api/admin/categories', async (req, res) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    const body = await readJsonBody(req, config.maxJsonBytes);

    let name;
    let slug;
    let description;
    let accentHex;
    let sortOrder;
    let isActive;
    try {
      name = requireString(body.name, 'Nombre', { min: 2, max: 60 });
      const rawSlug = body.slug ? String(body.slug) : buildSafeSlug(name, 'cat');
      slug = requireString(rawSlug, 'Slug', { min: 2, max: 64, pattern: SLUG_RE }).toLowerCase();
      description = optionalString(body.description, 'Descripcion', { max: 240, fallback: '' });
      accentHex = requireString(body.accentHex ?? '#3482FF', 'Color', { min: 7, max: 7, pattern: HEX_COLOR_RE });
      sortOrder = body.sortOrder === undefined ? 0 : requireInt(body.sortOrder, 'Orden', { min: 0, max: 9999 });
      isActive = body.isActive === undefined ? true : requireBoolean(body.isActive, 'Activo');
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    if (db.prepare('SELECT id FROM categories WHERE slug = ?').get(slug)) {
      if (!body.slug) {
        slug = `${slug.slice(0, 50)}-${Date.now().toString(36).slice(-4)}`;
      } else {
        throw new HttpError(409, 'Ya existe una categoria con ese slug');
      }
    }

    const now = Date.now();
    const resIns = db
      .prepare(`
        INSERT INTO categories (name, slug, description, accent_hex, sort_order, is_active, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `)
      .run(name, slug, description, accentHex, sortOrder, isActive ? 1 : 0, now, now);

    const id = Number(resIns.lastInsertRowid);
    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_CATEGORY_CREATE',
      targetType: 'category',
      targetId: String(id),
      metadata: { name, slug },
      ipHash,
    });

    const created = db
      .prepare(
        'SELECT id, name, slug, description, accent_hex AS accentHex, sort_order AS sortOrder, is_active AS isActive FROM categories WHERE id = ?',
      )
      .get(id);
    sendJson(res, 201, { category: created });
  });

  router.patch('/api/admin/categories/:id', async (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    const body = await readJsonBody(req, config.maxJsonBytes);

    const current = db.prepare('SELECT * FROM categories WHERE id = ?').get(id);
    if (!current) throw new HttpError(404, 'Categoria no encontrada');

    let name = current.name;
    let slug = current.slug;
    let description = current.description;
    let accentHex = current.accent_hex;
    let sortOrder = current.sort_order;
    let isActive = current.is_active === 1;

    try {
      if (body.name !== undefined && body.name !== null) name = requireString(body.name, 'Nombre', { min: 2, max: 60 });
      if (body.slug !== undefined && body.slug !== null && String(body.slug).trim() !== '') {
        slug = requireString(body.slug, 'Slug', { min: 2, max: 64, pattern: SLUG_RE }).toLowerCase();
      }
      if (body.description !== undefined && body.description !== null) {
        description = optionalString(body.description, 'Descripcion', { max: 240, fallback: '' });
      }
      if (body.accentHex !== undefined && body.accentHex !== null) {
        accentHex = requireString(body.accentHex, 'Color', { min: 7, max: 7, pattern: HEX_COLOR_RE });
      }
      if (body.sortOrder !== undefined && body.sortOrder !== null) {
        sortOrder = requireInt(body.sortOrder, 'Orden', { min: 0, max: 9999 });
      }
      if (body.isActive !== undefined && body.isActive !== null) {
        isActive = requireBoolean(body.isActive, 'Activo');
      }
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    const dup = db.prepare('SELECT id FROM categories WHERE slug = ? AND id != ?').get(slug, id);
    if (dup) throw new HttpError(409, 'Otra categoria ya usa ese slug');

    db.prepare(`
      UPDATE categories
      SET name = ?, slug = ?, description = ?, accent_hex = ?, sort_order = ?, is_active = ?, updated_at = ?
      WHERE id = ?
    `).run(name, slug, description, accentHex, sortOrder, isActive ? 1 : 0, Date.now(), id);

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_CATEGORY_UPDATE',
      targetType: 'category',
      targetId: String(id),
      metadata: { name, isActive },
      ipHash,
    });

    const updated = db
      .prepare(
        'SELECT id, name, slug, description, accent_hex AS accentHex, sort_order AS sortOrder, is_active AS isActive FROM categories WHERE id = ?',
      )
      .get(id);
    sendJson(res, 200, { category: updated });
  });

  router.delete('/api/admin/categories/:id', (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);

    const cat = db.prepare('SELECT id, name FROM categories WHERE id = ?').get(id);
    if (!cat) throw new HttpError(404, 'Categoria no encontrada');

    const imagesToRemove = db
      .prepare(`
        SELECT pi.storage_key
        FROM product_images pi
        JOIN products p ON p.id = pi.product_id
        WHERE p.category_id = ?
      `)
      .all(id);

    withTransaction(db, 'IMMEDIATE', () => {
      db.prepare('DELETE FROM products WHERE category_id = ?').run(id);
      db.prepare('DELETE FROM categories WHERE id = ?').run(id);
    });

    for (const img of imagesToRemove) {
      deleteStoredImageFile(config.uploadDir, img.storage_key);
    }

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_CATEGORY_DELETE',
      targetType: 'category',
      targetId: String(id),
      metadata: { name: cat.name },
      ipHash,
    });

    sendJson(res, 200, { ok: true });
  });

  // 3. Products CRUD
  router.get('/api/admin/products', (req, res) => {
    requireAdmin(req);
    const products = db
      .prepare(`
        SELECT
          p.id,
          p.category_id AS categoryId,
          c.name AS categoryName,
          p.founder_tag AS founderTag,
          p.name,
          p.slug,
          p.subtitle,
          p.description,
          p.price_cents AS priceCents,
          p.compare_at_cents AS compareAtCents,
          p.stock,
          p.is_active AS isActive,
          p.created_at AS createdAt,
          p.updated_at AS updatedAt
        FROM products p
        JOIN categories c ON c.id = p.category_id
        ORDER BY p.updated_at DESC, p.id DESC
      `)
      .all();

    const imgsStmt = db.prepare(`
      SELECT id, '/api/media/' || storage_key AS url, is_primary AS isPrimary, sort_order AS sortOrder
      FROM product_images
      WHERE product_id = ?
      ORDER BY is_primary DESC, sort_order ASC, id ASC
    `);

    const hydrated = products.map((p) => {
      const images = imgsStmt.all(p.id);
      return { ...p, images, primaryImageUrl: images[0]?.url ?? null };
    });

    sendJson(res, 200, { products: hydrated });
  });

  router.post('/api/admin/products', async (req, res) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    const body = await readJsonBody(req, config.maxJsonBytes);

    let categoryId;
    let founderTag;
    let name;
    let slug;
    let subtitle;
    let description;
    let priceCents;
    let compareAtCents;
    let stock;
    let isActive;

    const now = Date.now();
    try {
      categoryId = resolveOrCreateCategoryId(
        db,
        { categoryId: body.categoryId, categoryName: body.categoryName },
        now,
      );
      const inferredFounder = inferFounderFromText(body.categoryName) || inferFounderFromText(body.founderTag);
      const rawFounder = body.founderTag && FOUNDER_TAGS.has(body.founderTag) ? body.founderTag : (inferredFounder || 'Crew');
      founderTag = FOUNDER_TAGS.has(rawFounder) ? rawFounder : 'Crew';
      name = requireString(body.name, 'Nombre', { min: 2, max: 100 });
      const rawSlug = body.slug ? String(body.slug) : buildSafeSlug(name, 'prod');
      slug = requireString(rawSlug, 'Slug', { min: 2, max: 64, pattern: SLUG_RE }).toLowerCase();
      subtitle = optionalString(body.subtitle, 'Subtitulo', { max: 140, fallback: '' });
      description = optionalString(body.description, 'Descripcion', { max: 2000, fallback: '' });
      priceCents = requireInt(body.priceCents, 'Precio', { min: 1, max: 100_000_000 });
      compareAtCents = optionalPositiveInt(body.compareAtCents, 'Precio anterior', { min: 1, max: 100_000_000 });
      stock = requireInt(body.stock, 'Stock', { min: 0, max: 100_000 });
      isActive = body.isActive === undefined ? true : requireBoolean(body.isActive, 'Activo');
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    if (!db.prepare('SELECT id FROM categories WHERE id = ?').get(categoryId)) {
      throw new HttpError(404, 'La categoria indicada no existe');
    }
    if (db.prepare('SELECT id FROM products WHERE slug = ?').get(slug)) {
      if (!body.slug) {
        slug = `${slug.slice(0, 50)}-${now.toString(36).slice(-4)}`;
      } else {
        throw new HttpError(409, 'Ya existe un producto con ese slug');
      }
    }
    const resIns = db
      .prepare(`
        INSERT INTO products (
          category_id, founder_tag, name, slug, subtitle, description,
          price_cents, compare_at_cents, stock, is_active, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `)
      .run(
        categoryId,
        founderTag,
        name,
        slug,
        subtitle,
        description,
        priceCents,
        compareAtCents,
        stock,
        isActive ? 1 : 0,
        now,
        now,
      );

    const id = Number(resIns.lastInsertRowid);
    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_PRODUCT_CREATE',
      targetType: 'product',
      targetId: String(id),
      metadata: { name, priceCents, stock },
      ipHash,
    });

    const created = db
      .prepare(`
        SELECT id, category_id AS categoryId, founder_tag AS founderTag, name, slug, subtitle, description,
               price_cents AS priceCents, compare_at_cents AS compareAtCents, stock, is_active AS isActive
        FROM products WHERE id = ?
      `)
      .get(id);

    sendJson(res, 201, { product: { ...created, images: [], primaryImageUrl: null } });
  });

  router.patch('/api/admin/products/:id', async (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    const body = await readJsonBody(req, config.maxJsonBytes);

    const current = db.prepare('SELECT * FROM products WHERE id = ?').get(id);
    if (!current) throw new HttpError(404, 'Producto no encontrado');

    let categoryId = current.category_id;
    let founderTag = current.founder_tag;
    let name = current.name;
    let slug = current.slug;
    let subtitle = current.subtitle;
    let description = current.description;
    let priceCents = current.price_cents;
    let compareAtCents = current.compare_at_cents;
    let stock = current.stock;
    let isActive = current.is_active === 1;

    const now = Date.now();
    try {
      const hasCatName = typeof body.categoryName === 'string' && body.categoryName.trim().length > 0;
      const hasCatId = body.categoryId !== undefined && body.categoryId !== null;
      if (hasCatName || hasCatId) {
        categoryId = resolveOrCreateCategoryId(
          db,
          { categoryId: hasCatId ? body.categoryId : current.category_id, categoryName: hasCatName ? body.categoryName : null },
          now,
        );
      }
      if (body.founderTag !== undefined && body.founderTag !== null) {
        const inferred = inferFounderFromText(body.founderTag);
        founderTag = FOUNDER_TAGS.has(body.founderTag) ? body.founderTag : (inferred || 'Crew');
      } else if (hasCatName) {
        const inferred = inferFounderFromText(body.categoryName);
        if (inferred) founderTag = inferred;
      }
      if (body.name !== undefined && body.name !== null) {
        name = requireString(body.name, 'Nombre', { min: 2, max: 100 });
      }
      if (body.slug !== undefined && body.slug !== null && String(body.slug).trim() !== '') {
        slug = requireString(body.slug, 'Slug', { min: 2, max: 64, pattern: SLUG_RE }).toLowerCase();
      }
      if (body.subtitle !== undefined && body.subtitle !== null) {
        subtitle = optionalString(body.subtitle, 'Subtitulo', { max: 140, fallback: '' });
      }
      if (body.description !== undefined && body.description !== null) {
        description = optionalString(body.description, 'Descripcion', { max: 2000, fallback: '' });
      }
      if (body.priceCents !== undefined && body.priceCents !== null) {
        priceCents = requireInt(body.priceCents, 'Precio', { min: 1, max: 100_000_000 });
      }
      if (body.compareAtCents !== undefined) {
        compareAtCents = optionalPositiveInt(body.compareAtCents, 'Precio anterior', { min: 1, max: 100_000_000 });
      }
      if (body.stock !== undefined && body.stock !== null) {
        stock = requireInt(body.stock, 'Stock', { min: 0, max: 100_000 });
      }
      if (body.isActive !== undefined && body.isActive !== null) {
        isActive = requireBoolean(body.isActive, 'Activo');
      }
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    if (!db.prepare('SELECT id FROM categories WHERE id = ?').get(categoryId)) {
      throw new HttpError(404, 'La categoria indicada no existe');
    }
    if (db.prepare('SELECT id FROM products WHERE slug = ? AND id != ?').get(slug, id)) {
      throw new HttpError(409, 'Otro producto ya usa ese slug');
    }

    db.prepare(`
      UPDATE products
      SET category_id = ?, founder_tag = ?, name = ?, slug = ?, subtitle = ?, description = ?,
          price_cents = ?, compare_at_cents = ?, stock = ?, is_active = ?, updated_at = ?
      WHERE id = ?
    `).run(
      categoryId,
      founderTag,
      name,
      slug,
      subtitle,
      description,
      priceCents,
      compareAtCents,
      stock,
      isActive ? 1 : 0,
      Date.now(),
      id,
    );

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_PRODUCT_UPDATE',
      targetType: 'product',
      targetId: String(id),
      metadata: { name, priceCents, stock, isActive },
      ipHash,
    });

    const updated = db
      .prepare(`
        SELECT id, category_id AS categoryId, founder_tag AS founderTag, name, slug, subtitle, description,
               price_cents AS priceCents, compare_at_cents AS compareAtCents, stock, is_active AS isActive
        FROM products WHERE id = ?
      `)
      .get(id);
    sendJson(res, 200, { product: updated });
  });

  router.delete('/api/admin/products/:id', (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);

    const product = db.prepare('SELECT id, name FROM products WHERE id = ?').get(id);
    if (!product) throw new HttpError(404, 'Producto no encontrado');

    const images = db.prepare('SELECT storage_key FROM product_images WHERE product_id = ?').all(id);
    db.prepare('DELETE FROM products WHERE id = ?').run(id);
    for (const img of images) {
      deleteStoredImageFile(config.uploadDir, img.storage_key);
    }

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_PRODUCT_DELETE',
      targetType: 'product',
      targetId: String(id),
      metadata: { name: product.name },
      ipHash,
    });
    return sendJson(res, 200, { ok: true, archived: false });
  });

  // 4. Secure Image Upload & Management
  router.post('/api/admin/media', async (req, res) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);

    const rl = limiter.check(`upload:${req.user.id}`, { limit: 30, windowMs: 10 * 60_000 });
    if (!rl.allowed) {
      throw new HttpError(429, 'Limite de subida de imagenes alcanzado temporalmente');
    }

    const ct = String(req.headers['content-type'] || '');
    if (!ct.toLowerCase().includes('multipart/form-data')) {
      throw new HttpError(415, 'Se requiere multipart/form-data para subir imagenes');
    }

    // Reserve +128KB overhead for multipart headers on top of maxUploadBytes.
    const raw = await readRawBody(req, config.maxUploadBytes + 128 * 1024);
    let parsed;
    try {
      parsed = parseMultipartSingleFile(raw, ct);
    } catch (err) {
      if (err instanceof UploadError) throw new HttpError(err.status, err.message);
      throw err;
    }

    const productId = Number.parseInt(parsed.fields.productId || '', 10);
    if (!Number.isInteger(productId) || productId <= 0) {
      throw new HttpError(400, 'productId invalido');
    }
    const product = db.prepare('SELECT id FROM products WHERE id = ?').get(productId);
    if (!product) throw new HttpError(404, 'Producto no encontrado');

    const imgCount = db.prepare('SELECT COUNT(*) AS cnt FROM product_images WHERE product_id = ?').get(productId).cnt;
    if (imgCount >= 8) {
      throw new HttpError(400, 'Maximo 8 imagenes por producto');
    }

    let saved;
    try {
      saved = saveValidatedImage(parsed.fileBuffer, config.uploadDir, config.maxUploadBytes);
    } catch (err) {
      if (err instanceof UploadError) throw new HttpError(err.status, err.message);
      throw err;
    }

    const makePrimary = parsed.fields.isPrimary === 'true' || imgCount === 0;
    const now = Date.now();
    let mediaId;
    try {
      mediaId = withTransaction(db, 'IMMEDIATE', () => {
        if (makePrimary) {
          db.prepare('UPDATE product_images SET is_primary = 0 WHERE product_id = ?').run(productId);
        }
        const ins = db
          .prepare(`
            INSERT INTO product_images (
              product_id, storage_key, mime_type, byte_size, sha256, is_primary, sort_order, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          `)
          .run(productId, saved.storageKey, saved.mimeType, saved.byteSize, saved.sha256, makePrimary ? 1 : 0, imgCount, now);
        return Number(ins.lastInsertRowid);
      });
    } catch (err) {
      deleteStoredImageFile(config.uploadDir, saved.storageKey);
      throw err;
    }

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_MEDIA_UPLOAD',
      targetType: 'product_image',
      targetId: String(mediaId),
      metadata: { productId, mimeType: saved.mimeType, byteSize: saved.byteSize },
      ipHash,
    });

    sendJson(res, 201, {
      image: {
        id: mediaId,
        productId,
        url: `/api/media/${saved.storageKey}`,
        mimeType: saved.mimeType,
        byteSize: saved.byteSize,
        isPrimary: makePrimary ? 1 : 0,
      },
    });
  });

  router.patch('/api/admin/media/:id/primary', (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);

    const img = db.prepare('SELECT id, product_id FROM product_images WHERE id = ?').get(id);
    if (!img) throw new HttpError(404, 'Imagen no encontrada');

    withTransaction(db, 'IMMEDIATE', () => {
      db.prepare('UPDATE product_images SET is_primary = 0 WHERE product_id = ?').run(img.product_id);
      db.prepare('UPDATE product_images SET is_primary = 1 WHERE id = ?').run(id);
    });

    sendJson(res, 200, { ok: true });
  });

  router.delete('/api/admin/media/:id', (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);

    const img = db.prepare('SELECT id, product_id, storage_key, is_primary FROM product_images WHERE id = ?').get(id);
    if (!img) throw new HttpError(404, 'Imagen no encontrada');

    withTransaction(db, 'IMMEDIATE', () => {
      db.prepare('DELETE FROM product_images WHERE id = ?').run(id);
      if (img.is_primary === 1) {
        const next = db
          .prepare('SELECT id FROM product_images WHERE product_id = ? ORDER BY sort_order ASC, id ASC LIMIT 1')
          .get(img.product_id);
        if (next) {
          db.prepare('UPDATE product_images SET is_primary = 1 WHERE id = ?').run(next.id);
        }
      }
    });
    deleteStoredImageFile(config.uploadDir, img.storage_key);

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_MEDIA_DELETE',
      targetType: 'product_image',
      targetId: String(id),
      metadata: { productId: img.product_id },
      ipHash,
    });

    sendJson(res, 200, { ok: true });
  });

  // 5. Orders Management
  router.get('/api/admin/orders', (req, res, url) => {
    requireAdmin(req);
    const statusFilter = (url.searchParams.get('status') || '').trim();
    const whereSql = ORDER_STATUSES.has(statusFilter) ? 'WHERE o.status = ?' : '';
    const args = ORDER_STATUSES.has(statusFilter) ? [statusFilter] : [];

    const orders = db
      .prepare(`
        SELECT
          o.id,
          o.order_code AS orderCode,
          o.user_id AS userId,
          u.username,
          u.email,
          o.status,
          o.total_cents AS totalCents,
          o.shipping_name AS shippingName,
          o.shipping_note AS shippingNote,
          o.created_at AS createdAt,
          o.updated_at AS updatedAt,
          (SELECT COUNT(*) FROM order_messages om WHERE om.order_id = o.id) AS messageCount
        FROM orders o
        JOIN users u ON u.id = o.user_id
        ${whereSql}
        ORDER BY o.created_at DESC, o.id DESC
        LIMIT 200
      `)
      .all(...args);

    const itemsStmt = db.prepare(`
      SELECT id, product_id AS productId, product_name_snapshot AS productName,
             unit_price_cents AS unitPriceCents, quantity, line_total_cents AS lineTotalCents
      FROM order_items
      WHERE order_id = ?
    `);

    sendJson(res, 200, {
      orders: orders.map((o) => ({ ...o, items: itemsStmt.all(o.id) })),
    });
  });

  router.patch('/api/admin/orders/:id/status', async (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    const body = await readJsonBody(req, config.maxJsonBytes);

    let nextStatus;
    try {
      nextStatus = requireString(body.status, 'Estado', { min: 4, max: 20 });
      if (!ORDER_STATUSES.has(nextStatus)) throw new ValidationError('Estado de orden invalido');
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    const now = Date.now();
    withTransaction(db, 'IMMEDIATE', () => {
      const order = db.prepare('SELECT id, order_code, status FROM orders WHERE id = ?').get(id);
      if (!order) throw new HttpError(404, 'Orden no encontrada');
      if (order.status === nextStatus) return;

      const items = db.prepare('SELECT product_id, quantity, product_name_snapshot FROM order_items WHERE order_id = ?').all(id);

      // Restore stock atomically when transitioning into cancelled.
      if (order.status !== 'cancelled' && nextStatus === 'cancelled') {
        const restoreStmt = db.prepare('UPDATE products SET stock = stock + ?, updated_at = ? WHERE id = ?');
        for (const item of items) {
          if (item.product_id) {
            restoreStmt.run(item.quantity, now, item.product_id);
          }
        }
      }

      // Re-deduct stock atomically if re-activating a cancelled order.
      if (order.status === 'cancelled' && nextStatus !== 'cancelled') {
        const deductStmt = db.prepare('UPDATE products SET stock = stock - ?, updated_at = ? WHERE id = ? AND stock >= ?');
        for (const item of items) {
          if (!item.product_id) {
            throw new HttpError(409, `No se puede reactivar: producto ${item.product_name_snapshot} fue eliminado`);
          }
          const r = deductStmt.run(item.quantity, now, item.product_id, item.quantity);
          if (r.changes !== 1) {
            throw new HttpError(409, `Stock insuficiente para reactivar ${item.product_name_snapshot}`);
          }
        }
      }

      db.prepare('UPDATE orders SET status = ?, updated_at = ? WHERE id = ?').run(nextStatus, now, id);
      recordAudit(db, {
        actorUserId: req.user.id,
        action: 'ADMIN_ORDER_STATUS',
        targetType: 'order',
        targetId: order.order_code,
        metadata: { from: order.status, to: nextStatus },
        ipHash,
      });
    });

    sendJson(res, 200, { ok: true, status: nextStatus });
  });

  router.delete('/api/admin/orders/:id', (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);

    const order = db.prepare('SELECT id, order_code FROM orders WHERE id = ?').get(id);
    if (!order) throw new HttpError(404, 'Orden no encontrada');

    const chatImgs = db
      .prepare('SELECT image_storage_key FROM order_messages WHERE order_id = ? AND image_storage_key IS NOT NULL')
      .all(id);

    db.prepare('DELETE FROM orders WHERE id = ?').run(id);
    const chatDir = path.join(config.uploadDir, 'order-chat');
    for (const row of chatImgs) {
      deleteStoredImageFile(chatDir, row.image_storage_key);
    }

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_ORDER_DELETE',
      targetType: 'order',
      targetId: order.order_code,
      ipHash,
    });
    sendJson(res, 200, { ok: true });
  });

  // 6. Users, Active Sessions & Audit Logs
  router.get('/api/admin/users', (req, res) => {
    requireAdmin(req);
    const now = Date.now();
    const users = db
      .prepare(`
        SELECT
          u.id,
          u.username,
          u.email,
          u.display_name AS displayName,
          u.role,
          u.is_active AS isActive,
          u.failed_login_attempts AS failedAttempts,
          u.locked_until AS lockedUntil,
          u.created_at AS createdAt,
          (
            SELECT COUNT(*)
            FROM sessions s
            WHERE s.user_id = u.id AND s.revoked_at IS NULL AND s.expires_at > ?
          ) AS activeSessions
        FROM users u
        ORDER BY u.created_at DESC, u.id ASC
      `)
      .all(now);

    sendJson(res, 200, { users });
  });

  router.patch('/api/admin/users/:id', async (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    const body = await readJsonBody(req, config.maxJsonBytes);

    const target = db.prepare('SELECT id, username, role, is_active FROM users WHERE id = ?').get(id);
    if (!target) throw new HttpError(404, 'Usuario no encontrado');

    let isActive = target.is_active === 1;
    let role = target.role;
    try {
      if (body.isActive !== undefined && body.isActive !== null) isActive = requireBoolean(body.isActive, 'Activo');
      if (body.role !== undefined && body.role !== null) {
        role = requireString(body.role, 'Rol', { min: 4, max: 16 });
        if (role !== 'customer' && role !== 'admin') throw new ValidationError('Rol invalido');
      }
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    if (target.id === req.user.id && (!isActive || role !== 'admin')) {
      throw new HttpError(400, 'No puedes desactivar ni quitar privilegios a tu propia cuenta admin');
    }

    db.prepare('UPDATE users SET is_active = ?, role = ?, updated_at = ? WHERE id = ?').run(
      isActive ? 1 : 0,
      role,
      Date.now(),
      id,
    );

    let revoked = 0;
    if (!isActive || role !== target.role) {
      revoked = revokeAllUserSessions(db, id);
    }

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_USER_UPDATE',
      targetType: 'user',
      targetId: String(id),
      metadata: { username: target.username, isActive, role, revokedSessions: revoked },
      ipHash,
    });

    sendJson(res, 200, { ok: true, revokedSessions: revoked });
  });

  router.post('/api/admin/users/:id/revoke-sessions', (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);

    const target = db.prepare('SELECT id, username FROM users WHERE id = ?').get(id);
    if (!target) throw new HttpError(404, 'Usuario no encontrado');

    const revoked = revokeAllUserSessions(db, id);
    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_REVOKE_SESSIONS',
      targetType: 'user',
      targetId: String(id),
      metadata: { username: target.username, revoked },
      ipHash,
    });

    sendJson(res, 200, { ok: true, revoked });
  });

  router.delete('/api/admin/users/:id', (req, res, _url, params) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const id = parseIdParam(params.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);

    if (id === req.user.id) {
      throw new HttpError(400, 'No puedes eliminar tu propia cuenta administrador');
    }

    const target = db.prepare('SELECT id, username FROM users WHERE id = ?').get(id);
    if (!target) throw new HttpError(404, 'Usuario no encontrado');

    const chatImgs = db
      .prepare(`
        SELECT om.image_storage_key
        FROM order_messages om
        LEFT JOIN orders o ON o.id = om.order_id
        WHERE (om.sender_user_id = ? OR o.user_id = ?) AND om.image_storage_key IS NOT NULL
      `)
      .all(id, id);

    withTransaction(db, 'IMMEDIATE', () => {
      db.prepare('DELETE FROM order_messages WHERE sender_user_id = ?').run(id);
      db.prepare('DELETE FROM orders WHERE user_id = ?').run(id);
      db.prepare('DELETE FROM users WHERE id = ?').run(id);
    });

    const chatDir = path.join(config.uploadDir, 'order-chat');
    for (const row of chatImgs) {
      deleteStoredImageFile(chatDir, row.image_storage_key);
    }

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_USER_DELETE',
      targetType: 'user',
      targetId: String(id),
      metadata: { username: target.username },
      ipHash,
    });

    sendJson(res, 200, { ok: true });
  });

  router.post('/api/admin/reset-db', (req, res) => {
    requireAdmin(req);
    requireCsrf(req, config);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);

    const prodImgs = db.prepare('SELECT storage_key FROM product_images').all();
    const chatImgs = db.prepare('SELECT image_storage_key FROM order_messages WHERE image_storage_key IS NOT NULL').all();

    withTransaction(db, 'IMMEDIATE', () => {
      db.prepare('DELETE FROM order_messages').run();
      db.prepare('DELETE FROM order_items').run();
      db.prepare('DELETE FROM orders').run();
      db.prepare('DELETE FROM cart_items').run();
      db.prepare('DELETE FROM product_images').run();
      db.prepare('DELETE FROM products').run();
      db.prepare('DELETE FROM categories').run();
      db.prepare('DELETE FROM sessions WHERE user_id != ?').run(req.user.id);
      db.prepare('DELETE FROM users WHERE id != ?').run(req.user.id);
      db.prepare('DELETE FROM audit_logs').run();
    });

    for (const row of prodImgs) {
      deleteStoredImageFile(config.uploadDir, row.storage_key);
    }
    const chatDir = path.join(config.uploadDir, 'order-chat');
    for (const row of chatImgs) {
      deleteStoredImageFile(chatDir, row.image_storage_key);
    }

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ADMIN_RESET_DB',
      targetType: 'database',
      targetId: 'all',
      ipHash,
    });

    sendJson(res, 200, { ok: true });
  });

  router.get('/api/admin/audit-logs', (req, res, url) => {
    requireAdmin(req);
    const limit = Math.max(1, Math.min(100, Number.parseInt(url.searchParams.get('limit') || '50', 10) || 50));
    const logs = db
      .prepare(`
        SELECT
          al.id,
          al.actor_user_id AS actorUserId,
          u.username AS actorUsername,
          al.action,
          al.target_type AS targetType,
          al.target_id AS targetId,
          al.metadata_json AS metadataJson,
          al.created_at AS createdAt
        FROM audit_logs al
        LEFT JOIN users u ON u.id = al.actor_user_id
        ORDER BY al.created_at DESC, al.id DESC
        LIMIT ?
      `)
      .all(limit);

    sendJson(res, 200, { logs });
  });
}
