import crypto from 'node:crypto';

export function hashIp(ip, salt) {
  const safeIp = String(ip || 'unknown');
  return crypto.createHmac('sha256', salt).update(safeIp).digest('hex').slice(0, 24);
}

export function recordAudit(db, { actorUserId = null, action, targetType, targetId = '', metadata = {}, ipHash = '' }) {
  const now = Date.now();
  const safeMeta = JSON.stringify(metadata ?? {}).slice(0, 2000);
  db.prepare(`
    INSERT INTO audit_logs (actor_user_id, action, target_type, target_id, metadata_json, ip_hash, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `).run(
    actorUserId,
    String(action).slice(0, 80),
    String(targetType).slice(0, 60),
    String(targetId).slice(0, 80),
    safeMeta,
    String(ipHash).slice(0, 64),
    now,
  );
}
