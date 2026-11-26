import fs from 'node:fs';
import path from 'node:path';
import { HttpError, sendJson } from '../http/io.js';
import { applySecurityHeaders } from '../security/headers.js';
import { MEDIA_KEY_RE } from '../security/validate.js';

const SORT_SQL = Object.freeze({
  newest: 'p.created_at DESC, p.id DESC',
  price_asc: 'p.price_cents ASC, p.id DESC',
  price_desc: 'p.price_cents DESC, p.id DESC',
  stock_desc: 'p.stock DESC, p.id DESC',
});

const MIME_BY_EXT = Object.freeze({
  png: 'image/png',
  jpg: 'image/jpeg',
  webp: 'image/webp',
});

function escapeLike(str) {
  return String(str).replace(/[\\%_]/g, (m) => `\\${m}`);
}

export function registerStoreRoutes(router, { db, config }) {
  router.get('/api/health', (_req, res) => {
    sendJson(res, 200, { ok: true });
  });

  router.get('/api/categories', (_req, res) => {
    const rows = db
      .prepare(`
        SELECT
          c.id,
          c.name,
          c.slug,
          c.description,
          c.accent_hex AS accentHex,
          c.sort_order AS sortOrder,
          COUNT(p.id) AS productCount
        FROM categories c
        LEFT JOIN products p ON p.category_id = c.id AND p.is_active = 1
        WHERE c.is_active = 1
        GROUP BY c.id
        ORDER BY c.sort_order ASC, c.id ASC
      `)
      .all();

    sendJson(res, 200, { categories: rows });
  });

  router.get('/api/products', (req, res, url) => {
    const qRaw = (url.searchParams.get('q') || '').trim().slice(0, 80);
    const categorySlug = (url.searchParams.get('category') || '').trim().slice(0, 64);
    const founder = (url.searchParams.get('founder') || '').trim().slice(0, 32);
    const sortKey = (url.searchParams.get('sort') || 'newest').trim();
    const orderBy = SORT_SQL[sortKey] || SORT_SQL.newest;

    const page = Math.max(1, Math.min(1000, Number.parseInt(url.searchParams.get('page') || '1', 10) || 1));
    const limit = Math.max(1, Math.min(50, Number.parseInt(url.searchParams.get('limit') || '24', 10) || 24));
    const offset = (page - 1) * limit;

    const where = ['p.is_active = 1', 'c.is_active = 1'];
    const params = [];

    if (qRaw) {
      const pattern = `%${escapeLike(qRaw)}%`;
      where.push("(p.name LIKE ? ESCAPE '\\' OR p.subtitle LIKE ? ESCAPE '\\' OR p.description LIKE ? ESCAPE '\\')");
      params.push(pattern, pattern, pattern);
    }
    if (categorySlug) {
      where.push('c.slug = ?');
      params.push(categorySlug);
    }
    if (founder && founder !== 'Todos') {
      where.push('p.founder_tag = ?');
      params.push(founder);
    }

    const whereSql = where.join(' AND ');
    const totalRow = db
      .prepare(`
        SELECT COUNT(*) AS total
        FROM products p
        JOIN categories c ON c.id = p.category_id
        WHERE ${whereSql}
      `)
      .get(...params);

    const items = db
      .prepare(`
        SELECT
          p.id,
          p.category_id AS categoryId,
          c.name AS categoryName,
          c.slug AS categorySlug,
          c.accent_hex AS categoryAccent,
          p.founder_tag AS founderTag,
          p.name,
          p.slug,
          p.subtitle,
          p.description,
          p.price_cents AS priceCents,
          p.compare_at_cents AS compareAtCents,
          p.stock,
          p.created_at AS createdAt,
          (
            SELECT '/api/media/' || pi.storage_key
            FROM product_images pi
            WHERE pi.product_id = p.id
            ORDER BY pi.is_primary DESC, pi.sort_order ASC, pi.id ASC
            LIMIT 1
          ) AS primaryImageUrl
        FROM products p
        JOIN categories c ON c.id = p.category_id
        WHERE ${whereSql}
        ORDER BY ${orderBy}
        LIMIT ? OFFSET ?
      `)
      .all(...params, limit, offset);

    sendJson(res, 200, {
      items,
      total: totalRow?.total || 0,
      page,
      limit,
    });
  });

  router.get('/api/products/:slug', (_req, res, _url, params) => {
    const slug = String(params.slug || '').trim().slice(0, 80);
    if (!slug) throw new HttpError(400, 'Slug invalido');

    const product = db
      .prepare(`
        SELECT
          p.id,
          p.category_id AS categoryId,
          c.name AS categoryName,
          c.slug AS categorySlug,
          c.accent_hex AS categoryAccent,
          p.founder_tag AS founderTag,
          p.name,
          p.slug,
          p.subtitle,
          p.description,
          p.price_cents AS priceCents,
          p.compare_at_cents AS compareAtCents,
          p.stock,
          p.created_at AS createdAt
        FROM products p
        JOIN categories c ON c.id = p.category_id
        WHERE p.slug = ? AND p.is_active = 1 AND c.is_active = 1
        LIMIT 1
      `)
      .get(slug);

    if (!product) {
      throw new HttpError(404, 'Producto no encontrado');
    }

    const images = db
      .prepare(`
        SELECT
          id,
          '/api/media/' || storage_key AS url,
          mime_type AS mimeType,
          is_primary AS isPrimary,
          sort_order AS sortOrder
        FROM product_images
        WHERE product_id = ?
        ORDER BY is_primary DESC, sort_order ASC, id ASC
      `)
      .all(product.id);

    sendJson(res, 200, {
      product: {
        ...product,
        images,
        primaryImageUrl: images[0]?.url ?? null,
      },
    });
  });

  router.get('/api/media/:key', (_req, res, _url, params) => {
    const key = String(params.key || '');
    if (!MEDIA_KEY_RE.test(key)) {
      throw new HttpError(404, 'Archivo no encontrado');
    }

    const resolvedRoot = path.resolve(config.uploadDir);
    const targetPath = path.resolve(config.uploadDir, key);
    if (!targetPath.startsWith(resolvedRoot + path.sep)) {
      throw new HttpError(404, 'Archivo no encontrado');
    }

    if (!fs.existsSync(targetPath)) {
      throw new HttpError(404, 'Archivo no encontrado');
    }

    const ext = key.split('.').pop();
    const mimeType = MIME_BY_EXT[ext] || 'application/octet-stream';
    const bytes = fs.readFileSync(targetPath);

    applySecurityHeaders(res, { isMedia: true });
    res.writeHead(200, {
      'Content-Type': mimeType,
      'Content-Length': String(bytes.length),
      'Cache-Control': 'public, max-age=86400, immutable',
    });
    res.end(bytes);
  });
}
