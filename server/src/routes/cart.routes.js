import crypto from 'node:crypto';
import { withTransaction } from '../db/connection.js';
import { getClientIp, HttpError, readJsonBody, sendJson } from '../http/io.js';
import { requireCsrf } from '../middleware/auth.js';
import { requireAuth } from '../middleware/rbac.js';
import { optionalString, requireInt, requireString, ValidationError } from '../security/validate.js';
import { hashIp, recordAudit } from '../utils/audit.js';

function loadUserCart(db, userId) {
  const rows = db
    .prepare(`
      SELECT
        ci.product_id AS productId,
        ci.quantity,
        p.name,
        p.slug,
        p.price_cents AS priceCents,
        p.stock,
        p.founder_tag AS founderTag,
        c.name AS categoryName,
        (
          SELECT '/api/media/' || pi.storage_key
          FROM product_images pi
          WHERE pi.product_id = p.id
          ORDER BY pi.is_primary DESC, pi.sort_order ASC, pi.id ASC
          LIMIT 1
        ) AS primaryImageUrl
      FROM cart_items ci
      JOIN products p ON p.id = ci.product_id
      JOIN categories c ON c.id = p.category_id
      WHERE ci.user_id = ? AND p.is_active = 1 AND c.is_active = 1
      ORDER BY ci.updated_at DESC, ci.id DESC
    `)
    .all(userId);

  let subtotalCents = 0;
  let itemCount = 0;
  const items = rows.map((row) => {
    const effectiveQty = Math.min(row.quantity, Math.max(0, row.stock));
    const lineTotalCents = row.priceCents * effectiveQty;
    subtotalCents += lineTotalCents;
    itemCount += effectiveQty;
    return {
      ...row,
      lineTotalCents,
      stockExceeded: row.quantity > row.stock,
    };
  });

  return { items, itemCount, subtotalCents };
}

export function registerCartRoutes(router, { db, config, limiter }) {
  router.get('/api/cart', (req, res) => {
    requireAuth(req);
    sendJson(res, 200, loadUserCart(db, req.user.id));
  });

  router.put('/api/cart/items/:productId', async (req, res, _url, params) => {
    requireAuth(req);
    requireCsrf(req, config);

    const productId = Number.parseInt(String(params.productId), 10);
    if (!Number.isInteger(productId) || productId <= 0) {
      throw new HttpError(400, 'ID de producto invalido');
    }

    const body = await readJsonBody(req, config.maxJsonBytes);
    let quantity;
    try {
      quantity = requireInt(body.quantity, 'quantity', { min: 0, max: 99 });
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    if (quantity === 0) {
      db.prepare('DELETE FROM cart_items WHERE user_id = ? AND product_id = ?').run(req.user.id, productId);
      return sendJson(res, 200, loadUserCart(db, req.user.id));
    }

    const product = db
      .prepare(`
        SELECT p.id, p.name, p.stock
        FROM products p
        JOIN categories c ON c.id = p.category_id
        WHERE p.id = ? AND p.is_active = 1 AND c.is_active = 1
      `)
      .get(productId);

    if (!product) {
      throw new HttpError(404, 'Producto no disponible');
    }
    if (product.stock < quantity) {
      throw new HttpError(409, `Stock insuficiente: solo quedan ${product.stock} unidades de ${product.name}`);
    }

    const now = Date.now();
    db.prepare(`
      INSERT INTO cart_items (user_id, product_id, quantity, updated_at)
      VALUES (?, ?, ?, ?)
      ON CONFLICT(user_id, product_id) DO UPDATE SET quantity = excluded.quantity, updated_at = excluded.updated_at
    `).run(req.user.id, productId, quantity, now);

    return sendJson(res, 200, loadUserCart(db, req.user.id));
  });

  router.post('/api/cart/merge', async (req, res) => {
    requireAuth(req);
    requireCsrf(req, config);

    const body = await readJsonBody(req, config.maxJsonBytes);
    if (!Array.isArray(body.items) || body.items.length > 30) {
      throw new HttpError(400, 'Lista de items invalida');
    }

    const now = Date.now();
    withTransaction(db, 'IMMEDIATE', () => {
      const findProduct = db.prepare(`
        SELECT p.id, p.stock
        FROM products p
        JOIN categories c ON c.id = p.category_id
        WHERE p.id = ? AND p.is_active = 1 AND c.is_active = 1
      `);
      const findExisting = db.prepare('SELECT quantity FROM cart_items WHERE user_id = ? AND product_id = ?');
      const upsert = db.prepare(`
        INSERT INTO cart_items (user_id, product_id, quantity, updated_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(user_id, product_id) DO UPDATE SET quantity = excluded.quantity, updated_at = excluded.updated_at
      `);

      for (const rawItem of body.items) {
        if (!rawItem || typeof rawItem !== 'object') continue;
        const pid = rawItem.productId;
        const qty = rawItem.quantity;
        if (!Number.isInteger(pid) || pid <= 0 || !Number.isInteger(qty) || qty < 1 || qty > 99) continue;

        const product = findProduct.get(pid);
        if (!product || product.stock <= 0) continue;

        const cur = findExisting.get(req.user.id, pid)?.quantity || 0;
        const merged = Math.min(99, product.stock, cur + qty);
        if (merged >= 1) {
          upsert.run(req.user.id, pid, merged, now);
        }
      }
    });

    sendJson(res, 200, loadUserCart(db, req.user.id));
  });

  router.post('/api/checkout', async (req, res) => {
    requireAuth(req);
    requireCsrf(req, config);

    const rl = limiter.check(`checkout:${req.user.id}`, { limit: 12, windowMs: 5 * 60_000 });
    if (!rl.allowed) {
      throw new HttpError(429, 'Demasiadas ordenes seguidas. Espera un momento.');
    }

    const body = await readJsonBody(req, config.maxJsonBytes);
    let shippingName;
    let shippingNote;
    try {
      shippingName = requireString(body.shippingName ?? req.user.displayName, 'Nombre de entrega', {
        min: 2,
        max: 80,
      });
      shippingNote = optionalString(body.shippingNote, 'Nota de entrega', { max: 240, fallback: '' });
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    const now = Date.now();

    const createdOrder = withTransaction(db, 'IMMEDIATE', () => {
      const cartRows = db
        .prepare(`
          SELECT
            ci.product_id AS productId,
            ci.quantity,
            p.name,
            p.price_cents AS priceCents,
            p.stock,
            p.is_active AS productActive,
            c.is_active AS categoryActive
          FROM cart_items ci
          JOIN products p ON p.id = ci.product_id
          JOIN categories c ON c.id = p.category_id
          WHERE ci.user_id = ?
        `)
        .all(req.user.id);

      if (cartRows.length === 0) {
        throw new HttpError(400, 'Tu carrito esta vacio');
      }

      const deductStock = db.prepare(`
        UPDATE products
        SET stock = stock - ?, updated_at = ?
        WHERE id = ? AND is_active = 1 AND stock >= ?
      `);

      let subtotalCents = 0;
      const lineSnapshots = [];

      for (const item of cartRows) {
        if (item.productActive !== 1 || item.categoryActive !== 1) {
          throw new HttpError(409, `${item.name} ya no esta disponible`);
        }
        const upd = deductStock.run(item.quantity, now, item.productId, item.quantity);
        if (upd.changes !== 1) {
          throw new HttpError(409, `Stock insuficiente para ${item.name} (disponible: ${item.stock})`);
        }
        const lineTotalCents = item.priceCents * item.quantity;
        subtotalCents += lineTotalCents;
        lineSnapshots.push({
          productId: item.productId,
          productNameSnapshot: item.name,
          unitPriceCents: item.priceCents,
          quantity: item.quantity,
          lineTotalCents,
        });
      }

      const orderCode = `TBD-${new Date(now).getUTCFullYear()}-${crypto.randomBytes(3).toString('hex').toUpperCase()}`;
      const orderRes = db
        .prepare(`
          INSERT INTO orders (
            order_code, user_id, status, subtotal_cents, total_cents,
            shipping_name, shipping_note, created_at, updated_at
          ) VALUES (?, ?, 'pending', ?, ?, ?, ?, ?, ?)
        `)
        .run(orderCode, req.user.id, subtotalCents, subtotalCents, shippingName, shippingNote, now, now);

      const orderId = Number(orderRes.lastInsertRowid);
      const insertOrderItem = db.prepare(`
        INSERT INTO order_items (
          order_id, product_id, product_name_snapshot, unit_price_cents, quantity, line_total_cents
        ) VALUES (?, ?, ?, ?, ?, ?)
      `);

      for (const snap of lineSnapshots) {
        insertOrderItem.run(
          orderId,
          snap.productId,
          snap.productNameSnapshot,
          snap.unitPriceCents,
          snap.quantity,
          snap.lineTotalCents,
        );
      }

      db.prepare('DELETE FROM cart_items WHERE user_id = ?').run(req.user.id);

      recordAudit(db, {
        actorUserId: req.user.id,
        action: 'ORDER_CREATED',
        targetType: 'order',
        targetId: orderCode,
        metadata: { totalCents: subtotalCents, items: lineSnapshots.length },
        ipHash,
      });

      return {
        id: orderId,
        orderCode,
        status: 'pending',
        subtotalCents,
        totalCents: subtotalCents,
        shippingName,
        shippingNote,
        createdAt: now,
        items: lineSnapshots.map((s) => ({
          productId: s.productId,
          productName: s.productNameSnapshot,
          unitPriceCents: s.unitPriceCents,
          quantity: s.quantity,
          lineTotalCents: s.lineTotalCents,
        })),
      };
    });

    sendJson(res, 201, { order: createdOrder });
  });
}
