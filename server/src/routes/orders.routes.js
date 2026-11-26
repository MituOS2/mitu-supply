import fs from 'node:fs';
import path from 'node:path';
import { getClientIp, HttpError, readJsonBody, readRawBody, sendJson } from '../http/io.js';
import { requireCsrf } from '../middleware/auth.js';
import { requireAuth } from '../middleware/rbac.js';
import { applySecurityHeaders } from '../security/headers.js';
import { deleteStoredImageFile, parseMultipartSingleFile, saveValidatedImage, UploadError } from '../security/upload.js';
import { MEDIA_KEY_RE, optionalString, ValidationError } from '../security/validate.js';
import { hashIp, recordAudit } from '../utils/audit.js';

const MIME_BY_EXT = Object.freeze({
  png: 'image/png',
  jpg: 'image/jpeg',
  webp: 'image/webp',
});

function loadAccessibleOrder(db, orderCode, user) {
  const cleanCode = String(orderCode || '').trim().slice(0, 40);
  if (!cleanCode) {
    throw new HttpError(404, 'Orden no encontrada');
  }

  const order = db
    .prepare(`
      SELECT
        o.id,
        o.order_code AS orderCode,
        o.user_id AS userId,
        u.username,
        u.email,
        o.status,
        o.subtotal_cents AS subtotalCents,
        o.total_cents AS totalCents,
        o.shipping_name AS shippingName,
        o.shipping_note AS shippingNote,
        o.created_at AS createdAt,
        o.updated_at AS updatedAt
      FROM orders o
      JOIN users u ON u.id = o.user_id
      WHERE o.order_code = ?
      LIMIT 1
    `)
    .get(cleanCode);

  if (!order) {
    throw new HttpError(404, 'Orden no encontrada');
  }

  // Strictly scoped to order owner or admin (Anti-IDOR).
  if (user.role !== 'admin' && order.userId !== user.id) {
    throw new HttpError(404, 'Orden no encontrada');
  }

  return order;
}

function serializeMessage(row, orderCode) {
  return {
    id: row.id,
    orderId: row.orderId,
    senderUserId: row.senderUserId,
    senderUsername: row.senderUsername,
    senderDisplayName: row.senderDisplayName,
    senderRole: row.senderRole,
    messageText: row.messageText,
    imageUrl: row.imageStorageKey
      ? `/api/orders/${encodeURIComponent(orderCode)}/attachments/${row.imageStorageKey}`
      : null,
    createdAt: row.createdAt,
  };
}

function listOrderMessages(db, orderId, orderCode) {
  const rows = db
    .prepare(`
      SELECT
        m.id,
        m.order_id AS orderId,
        m.sender_user_id AS senderUserId,
        u.username AS senderUsername,
        u.display_name AS senderDisplayName,
        m.sender_role AS senderRole,
        m.message_text AS messageText,
        m.image_storage_key AS imageStorageKey,
        m.created_at AS createdAt
      FROM order_messages m
      JOIN users u ON u.id = m.sender_user_id
      WHERE m.order_id = ?
      ORDER BY m.created_at ASC, m.id ASC
      LIMIT 200
    `)
    .all(orderId);

  return rows.map((row) => serializeMessage(row, orderCode));
}

export function registerOrderRoutes(router, { db, config, limiter }) {
  const chatUploadDir = path.join(config.uploadDir, 'order-chat');

  router.get('/api/orders', (req, res) => {
    requireAuth(req);
    const orders = db
      .prepare(`
        SELECT
          o.id,
          o.order_code AS orderCode,
          o.status,
          o.subtotal_cents AS subtotalCents,
          o.total_cents AS totalCents,
          o.shipping_name AS shippingName,
          o.shipping_note AS shippingNote,
          o.created_at AS createdAt,
          o.updated_at AS updatedAt,
          (SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi WHERE oi.order_id = o.id) AS itemCount,
          (SELECT COUNT(*) FROM order_messages om WHERE om.order_id = o.id) AS messageCount
        FROM orders o
        WHERE o.user_id = ?
        ORDER BY o.created_at DESC, o.id DESC
        LIMIT 100
      `)
      .all(req.user.id);

    sendJson(res, 200, { orders });
  });

  router.get('/api/orders/:orderCode', (req, res, _url, params) => {
    requireAuth(req);
    const order = loadAccessibleOrder(db, params.orderCode, req.user);

    const items = db
      .prepare(`
        SELECT
          id,
          product_id AS productId,
          product_name_snapshot AS productName,
          unit_price_cents AS unitPriceCents,
          quantity,
          line_total_cents AS lineTotalCents
        FROM order_items
        WHERE order_id = ?
        ORDER BY id ASC
      `)
      .all(order.id);

    const messages = listOrderMessages(db, order.id, order.orderCode);
    sendJson(res, 200, { order: { ...order, items, messageCount: messages.length, messages } });
  });

  router.get('/api/orders/:orderCode/messages', (req, res, _url, params) => {
    requireAuth(req);
    const order = loadAccessibleOrder(db, params.orderCode, req.user);
    const messages = listOrderMessages(db, order.id, order.orderCode);
    sendJson(res, 200, { orderCode: order.orderCode, messages });
  });

  router.post('/api/orders/:orderCode/messages', async (req, res, _url, params) => {
    requireAuth(req);
    requireCsrf(req, config);

    const rl = limiter.check(`order-msg:${req.user.id}`, { limit: 60, windowMs: 5 * 60_000 });
    if (!rl.allowed) {
      throw new HttpError(429, 'Demasiados mensajes seguidos. Espera un momento.');
    }

    const order = loadAccessibleOrder(db, params.orderCode, req.user);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    const ct = String(req.headers['content-type'] || '').toLowerCase();

    let rawMessage = '';
    let saved = null;

    if (ct.includes('multipart/form-data')) {
      const raw = await readRawBody(req, config.maxUploadBytes + 128 * 1024);
      let parsed;
      try {
        parsed = parseMultipartSingleFile(raw, String(req.headers['content-type'] || ''), { optionalFile: true });
      } catch (err) {
        if (err instanceof UploadError) throw new HttpError(err.status, err.message);
        throw err;
      }
      rawMessage = parsed.fields.message || '';
      if (parsed.fileBuffer) {
        try {
          saved = saveValidatedImage(parsed.fileBuffer, chatUploadDir, config.maxUploadBytes);
        } catch (err) {
          if (err instanceof UploadError) throw new HttpError(err.status, err.message);
          throw err;
        }
      }
    } else if (ct.includes('application/json')) {
      const body = await readJsonBody(req, config.maxJsonBytes);
      rawMessage = body.message ?? '';
    } else {
      throw new HttpError(415, 'Usa JSON o multipart/form-data para enviar mensajes');
    }

    let messageText;
    try {
      messageText = optionalString(rawMessage, 'Mensaje', { max: 1000, fallback: '' });
    } catch (err) {
      if (saved) deleteStoredImageFile(chatUploadDir, saved.storageKey);
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    if (!messageText && !saved) {
      throw new HttpError(400, 'Escribe un mensaje o adjunta una imagen');
    }

    const now = Date.now();
    const senderRole = req.user.role === 'admin' ? 'admin' : 'customer';
    let msgId;

    try {
      const ins = db
        .prepare(`
          INSERT INTO order_messages (
            order_id, sender_user_id, sender_role, message_text,
            image_storage_key, image_mime_type, image_byte_size, created_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `)
        .run(
          order.id,
          req.user.id,
          senderRole,
          messageText,
          saved ? saved.storageKey : null,
          saved ? saved.mimeType : null,
          saved ? saved.byteSize : null,
          now,
        );
      msgId = Number(ins.lastInsertRowid);
      db.prepare('UPDATE orders SET updated_at = ? WHERE id = ?').run(now, order.id);
    } catch (err) {
      if (saved) deleteStoredImageFile(chatUploadDir, saved.storageKey);
      throw err;
    }

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'ORDER_MESSAGE_SENT',
      targetType: 'order',
      targetId: order.orderCode,
      metadata: { messageId: msgId, hasImage: Boolean(saved), role: senderRole },
      ipHash,
    });

    const createdRow = db
      .prepare(`
        SELECT
          m.id,
          m.order_id AS orderId,
          m.sender_user_id AS senderUserId,
          u.username AS senderUsername,
          u.display_name AS senderDisplayName,
          m.sender_role AS senderRole,
          m.message_text AS messageText,
          m.image_storage_key AS imageStorageKey,
          m.created_at AS createdAt
        FROM order_messages m
        JOIN users u ON u.id = m.sender_user_id
        WHERE m.id = ?
      `)
      .get(msgId);

    sendJson(res, 201, { message: serializeMessage(createdRow, order.orderCode) });
  });

  router.get('/api/orders/:orderCode/attachments/:key', (req, res, _url, params) => {
    requireAuth(req);
    const order = loadAccessibleOrder(db, params.orderCode, req.user);
    const key = String(params.key || '');
    if (!MEDIA_KEY_RE.test(key)) {
      throw new HttpError(404, 'Archivo no encontrado');
    }

    const row = db
      .prepare('SELECT id FROM order_messages WHERE order_id = ? AND image_storage_key = ? LIMIT 1')
      .get(order.id, key);
    if (!row) {
      throw new HttpError(404, 'Archivo no encontrado');
    }

    const resolvedRoot = path.resolve(chatUploadDir);
    const targetPath = path.resolve(chatUploadDir, key);
    if (!targetPath.startsWith(resolvedRoot + path.sep) || !fs.existsSync(targetPath)) {
      throw new HttpError(404, 'Archivo no encontrado');
    }

    const ext = key.split('.').pop();
    const mimeType = MIME_BY_EXT[ext] || 'application/octet-stream';
    const bytes = fs.readFileSync(targetPath);

    applySecurityHeaders(res, { isMedia: true });
    res.writeHead(200, {
      'Content-Type': mimeType,
      'Content-Length': String(bytes.length),
      'Cache-Control': 'private, max-age=3600',
    });
    res.end(bytes);
  });
}
