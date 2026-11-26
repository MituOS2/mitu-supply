import { getClientIp, HttpError, readJsonBody, sendJson } from '../http/io.js';
import { requireCsrf, requireSameOrigin } from '../middleware/auth.js';
import { requireAuth } from '../middleware/rbac.js';
import { hashPassword, verifyPassword } from '../security/password.js';
import {
  buildClearSessionCookie,
  buildSessionCookie,
  createSession,
  revokeAllUserSessions,
  revokeSessionByToken,
} from '../security/session.js';
import { EMAIL_RE, requireString, USERNAME_RE, ValidationError } from '../security/validate.js';
import { hashIp, recordAudit } from '../utils/audit.js';

// Precomputed scrypt dummy hash to keep failed login timing constant even when user does not exist.
const DUMMY_SCRYPT_HASH =
  'scrypt$16384$8$1$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';

function serializeUser(u) {
  return {
    id: u.id,
    username: u.username,
    email: u.email,
    displayName: u.displayName ?? u.display_name,
    role: u.role,
  };
}

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

export function registerAuthRoutes(router, { db, config, limiter }) {
  router.get('/api/auth/me', (req, res) => {
    if (!req.session || !req.user) {
      if (config.localAutoAdmin && isDirectLocalhost(req)) {
        const adminUser = db
          .prepare(
            "SELECT id, username, email, display_name, role FROM users WHERE role = 'admin' AND is_active = 1 ORDER BY id ASC LIMIT 1",
          )
          .get();
        if (adminUser) {
          const ipHash = hashIp('127.0.0.1', config.ipHashSalt);
          const session = createSession(db, config, {
            userId: adminUser.id,
            userAgent: req.headers['user-agent'],
            ipHash,
          });
          return sendJson(
            res,
            200,
            {
              user: serializeUser(adminUser),
              csrfToken: session.csrfToken,
            },
            { 'Set-Cookie': buildSessionCookie(session.rawToken, config, req) },
          );
        }
      }
      return sendJson(res, 200, { user: null, csrfToken: null });
    }
    return sendJson(res, 200, {
      user: serializeUser(req.user),
      csrfToken: req.session.csrfToken,
    });
  });

  router.post('/api/auth/register', async (req, res) => {
    requireSameOrigin(req, config);
    const ip = getClientIp(req, config);
    const ipHash = hashIp(ip, config.ipHashSalt);
    const rl = limiter.check(`register:${ipHash}`, { limit: 10, windowMs: 15 * 60_000 });
    if (!rl.allowed) {
      throw new HttpError(429, 'Demasiados registros desde esta red. Intenta luego.', {
        'Retry-After': String(rl.retryAfterSec),
      });
    }

    const body = await readJsonBody(req, config.maxJsonBytes);
    let rawUsername;
    let username;
    let email;
    let displayName;
    let password;
    try {
      rawUsername = requireString(body.username, 'Nombre de usuario', { min: 3, max: 32, pattern: USERNAME_RE });
      username = rawUsername.toLowerCase();
      email = requireString(body.email, 'Correo', { min: 5, max: 120, pattern: EMAIL_RE }).toLowerCase();
      const rawDisplay = typeof body.displayName === 'string' ? body.displayName.trim() : '';
      displayName = rawDisplay.length > 0
        ? requireString(rawDisplay, 'Nombre visible', { min: 2, max: 50 })
        : rawUsername;
      password = requireString(body.password, 'Contrasena', { min: 6, max: 128, trim: false });
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    const existing = db
      .prepare('SELECT id FROM users WHERE username = ? OR email = ? LIMIT 1')
      .get(username, email);
    if (existing) {
      throw new HttpError(409, 'Ese usuario o correo ya no esta disponible');
    }

    const passwordHash = await hashPassword(password);
    const now = Date.now();
    const insertRes = db
      .prepare(`
        INSERT INTO users (username, email, display_name, password_hash, role, is_active, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'customer', 1, ?, ?)
      `)
      .run(username, email, displayName, passwordHash, now, now);

    const userId = Number(insertRes.lastInsertRowid);
    if (req.rawSessionToken) {
      revokeSessionByToken(db, config, req.rawSessionToken);
    }

    const session = createSession(db, config, {
      userId,
      userAgent: req.headers['user-agent'],
      ipHash,
    });

    recordAudit(db, {
      actorUserId: userId,
      action: 'REGISTER',
      targetType: 'user',
      targetId: String(userId),
      metadata: { username },
      ipHash,
    });

    return sendJson(
      res,
      201,
      {
        user: { id: userId, username, email, displayName, role: 'customer' },
        csrfToken: session.csrfToken,
      },
      { 'Set-Cookie': buildSessionCookie(session.rawToken, config, req) },
    );
  });

  router.post('/api/auth/login', async (req, res) => {
    requireSameOrigin(req, config);
    const ip = getClientIp(req, config);
    const ipHash = hashIp(ip, config.ipHashSalt);
    const rl = limiter.check(`login:${ipHash}`, { limit: 20, windowMs: 10 * 60_000 });
    if (!rl.allowed) {
      throw new HttpError(429, 'Demasiados intentos de acceso. Espera unos minutos.', {
        'Retry-After': String(rl.retryAfterSec),
      });
    }

    const body = await readJsonBody(req, config.maxJsonBytes);
    let identifier;
    let password;
    try {
      identifier = requireString(body.identifier, 'Usuario o correo', { min: 2, max: 120 }).toLowerCase();
      password = requireString(body.password, 'Contrasena', { min: 1, max: 128, trim: false });
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    const user = db
      .prepare(`
        SELECT id, username, email, display_name, password_hash, role, is_active, failed_login_attempts, locked_until
        FROM users
        WHERE username = ? OR email = ?
        LIMIT 1
      `)
      .get(identifier, identifier);

    const now = Date.now();
    if (!user) {
      await verifyPassword(password, DUMMY_SCRYPT_HASH);
      throw new HttpError(401, 'Credenciales invalidas');
    }

    if (user.locked_until && user.locked_until > now) {
      await verifyPassword(password, DUMMY_SCRYPT_HASH);
      throw new HttpError(429, 'Cuenta bloqueada temporalmente por intentos fallidos. Intenta mas tarde.');
    }

    const ok = await verifyPassword(password, user.password_hash);
    if (!ok || user.is_active !== 1) {
      const nextFails = (user.failed_login_attempts || 0) + 1;
      const shouldLock = nextFails >= 5;
      const lockedUntil = shouldLock ? now + 10 * 60_000 : null;
      db.prepare('UPDATE users SET failed_login_attempts = ?, locked_until = ?, updated_at = ? WHERE id = ?').run(
        shouldLock ? 0 : nextFails,
        lockedUntil,
        now,
        user.id,
      );
      recordAudit(db, {
        actorUserId: user.id,
        action: shouldLock ? 'LOGIN_LOCKED' : 'LOGIN_FAILED',
        targetType: 'user',
        targetId: String(user.id),
        metadata: { attempts: nextFails, active: user.is_active === 1 },
        ipHash,
      });
      if (shouldLock) {
        throw new HttpError(429, 'Demasiados intentos fallidos. Cuenta bloqueada por 10 minutos.');
      }
      throw new HttpError(401, 'Credenciales invalidas');
    }

    db.prepare('UPDATE users SET failed_login_attempts = 0, locked_until = NULL, updated_at = ? WHERE id = ?').run(
      now,
      user.id,
    );

    if (req.rawSessionToken) {
      revokeSessionByToken(db, config, req.rawSessionToken);
    }

    const session = createSession(db, config, {
      userId: user.id,
      userAgent: req.headers['user-agent'],
      ipHash,
    });

    recordAudit(db, {
      actorUserId: user.id,
      action: 'LOGIN_SUCCESS',
      targetType: 'user',
      targetId: String(user.id),
      metadata: { role: user.role },
      ipHash,
    });

    return sendJson(
      res,
      200,
      {
        user: serializeUser(user),
        csrfToken: session.csrfToken,
      },
      { 'Set-Cookie': buildSessionCookie(session.rawToken, config, req) },
    );
  });

  router.post('/api/auth/logout', (req, res) => {
    requireCsrf(req, config);
    revokeSessionByToken(db, config, req.rawSessionToken);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    recordAudit(db, {
      actorUserId: req.user?.id ?? null,
      action: 'LOGOUT',
      targetType: 'session',
      targetId: String(req.user?.id ?? ''),
      ipHash,
    });
    return sendJson(
      res,
      200,
      { ok: true },
      { 'Set-Cookie': buildClearSessionCookie(config, req) },
    );
  });

  router.post('/api/auth/logout-all', (req, res) => {
    requireAuth(req);
    requireCsrf(req, config);
    const revoked = revokeAllUserSessions(db, req.user.id);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'LOGOUT_ALL',
      targetType: 'user',
      targetId: String(req.user.id),
      metadata: { revoked },
      ipHash,
    });
    return sendJson(
      res,
      200,
      { ok: true, revoked },
      { 'Set-Cookie': buildClearSessionCookie(config, req) },
    );
  });

  router.post('/api/auth/change-password', async (req, res) => {
    requireAuth(req);
    requireCsrf(req, config);
    const ipHash = hashIp(getClientIp(req, config), config.ipHashSalt);
    const body = await readJsonBody(req, config.maxJsonBytes);

    let currentPassword;
    let newPassword;
    try {
      currentPassword = requireString(body.currentPassword, 'Contrasena actual', { min: 1, max: 128, trim: false });
      newPassword = requireString(body.newPassword, 'Nueva contrasena', { min: 6, max: 128, trim: false });
      if (currentPassword === newPassword) {
        throw new ValidationError('La nueva contrasena debe ser distinta a la actual');
      }
    } catch (err) {
      if (err instanceof ValidationError) throw new HttpError(400, err.message);
      throw err;
    }

    const userRow = db.prepare('SELECT id, password_hash FROM users WHERE id = ?').get(req.user.id);
    if (!userRow || !(await verifyPassword(currentPassword, userRow.password_hash))) {
      throw new HttpError(401, 'La contrasena actual no coincide');
    }

    const nextHash = await hashPassword(newPassword);
    const now = Date.now();
    db.prepare('UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?').run(nextHash, now, req.user.id);
    revokeAllUserSessions(db, req.user.id);

    const newSession = createSession(db, config, {
      userId: req.user.id,
      userAgent: req.headers['user-agent'],
      ipHash,
    });

    recordAudit(db, {
      actorUserId: req.user.id,
      action: 'PASSWORD_CHANGED',
      targetType: 'user',
      targetId: String(req.user.id),
      ipHash,
    });

    return sendJson(
      res,
      200,
      { ok: true, csrfToken: newSession.csrfToken },
      { 'Set-Cookie': buildSessionCookie(newSession.rawToken, config, req) },
    );
  });
}
