import crypto from 'node:crypto';

export const SESSION_COOKIE_NAME = 'tbd_sid';

export function hashSessionToken(rawToken, pepper) {
  return crypto.createHmac('sha256', pepper).update(String(rawToken)).digest('hex');
}

export function hashUserAgent(ua) {
  return crypto.createHash('sha256').update(String(ua || '')).digest('hex').slice(0, 24);
}

export function parseCookies(cookieHeader) {
  const cookies = Object.create(null);
  if (!cookieHeader || typeof cookieHeader !== 'string') return cookies;
  const pairs = cookieHeader.split(';');
  for (const pair of pairs) {
    const eq = pair.indexOf('=');
    if (eq <= 0) continue;
    const name = pair.slice(0, eq).trim();
    const val = pair.slice(eq + 1).trim();
    if (!name || name === '__proto__' || name === 'constructor' || name === 'prototype') continue;
    try {
      cookies[name] = decodeURIComponent(val);
    } catch {
      cookies[name] = val;
    }
  }
  return cookies;
}

export function createSession(db, config, { userId, userAgent, ipHash }) {
  const rawToken = crypto.randomBytes(32).toString('base64url');
  const csrfToken = crypto.randomBytes(32).toString('base64url');
  const tokenHash = hashSessionToken(rawToken, config.sessionPepper);
  const now = Date.now();
  const expiresAt = now + config.sessionTtlMs;

  db.prepare(`
    INSERT INTO sessions (token_hash, user_id, csrf_token, ua_hash, ip_hash, expires_at, last_seen_at, revoked_at, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?)
  `).run(tokenHash, userId, csrfToken, hashUserAgent(userAgent), ipHash, expiresAt, now, now);

  return { rawToken, csrfToken, tokenHash, expiresAt };
}

export function findActiveSession(db, config, rawToken) {
  if (!rawToken || typeof rawToken !== 'string' || rawToken.length < 20 || rawToken.length > 200) {
    return null;
  }
  const tokenHash = hashSessionToken(rawToken, config.sessionPepper);
  const now = Date.now();

  const row = db.prepare(`
    SELECT
      s.token_hash,
      s.user_id,
      s.csrf_token,
      s.expires_at,
      s.last_seen_at,
      s.revoked_at,
      u.username,
      u.email,
      u.display_name,
      u.role,
      u.is_active
    FROM sessions s
    JOIN users u ON u.id = s.user_id
    WHERE s.token_hash = ?
  `).get(tokenHash);

  if (!row) return null;
  if (row.revoked_at !== null) return null;
  if (row.expires_at <= now) return null;
  if (now - row.last_seen_at > config.sessionIdleTtlMs) {
    db.prepare('UPDATE sessions SET revoked_at = ? WHERE token_hash = ?').run(now, tokenHash);
    return null;
  }
  if (row.is_active !== 1) return null;

  if (now - row.last_seen_at > 60_000) {
    db.prepare('UPDATE sessions SET last_seen_at = ? WHERE token_hash = ?').run(now, tokenHash);
  }

  return {
    tokenHash: row.token_hash,
    csrfToken: row.csrf_token,
    expiresAt: row.expires_at,
    user: {
      id: row.user_id,
      username: row.username,
      email: row.email,
      displayName: row.display_name,
      role: row.role,
    },
  };
}

export function revokeSessionByToken(db, config, rawToken) {
  if (!rawToken) return;
  const tokenHash = hashSessionToken(rawToken, config.sessionPepper);
  db.prepare('UPDATE sessions SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL').run(Date.now(), tokenHash);
}

export function revokeAllUserSessions(db, userId) {
  const result = db
    .prepare('UPDATE sessions SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL')
    .run(Date.now(), userId);
  return result.changes;
}

export function buildSessionCookie(rawToken, config, req) {
  const maxAgeSec = Math.floor(config.sessionTtlMs / 1000);
  const parts = [
    `${SESSION_COOKIE_NAME}=${encodeURIComponent(rawToken)}`,
    'Path=/',
    'HttpOnly',
    'SameSite=Strict',
    `Max-Age=${maxAgeSec}`,
  ];
  const isHttps = req?.socket?.encrypted === true || (config.trustProxy && req?.headers?.['x-forwarded-proto'] === 'https');
  if (config.cookieSecure && isHttps) {
    parts.push('Secure');
  }
  return parts.join('; ');
}

export function buildClearSessionCookie(config, req) {
  const parts = [
    `${SESSION_COOKIE_NAME}=`,
    'Path=/',
    'HttpOnly',
    'SameSite=Strict',
    'Max-Age=0',
  ];
  const isHttps = req?.socket?.encrypted === true || (config.trustProxy && req?.headers?.['x-forwarded-proto'] === 'https');
  if (config.cookieSecure && isHttps) {
    parts.push('Secure');
  }
  return parts.join('; ');
}
