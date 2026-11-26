import { HttpError } from '../http/io.js';
import { isSameOriginRequest, verifyCsrfToken } from '../security/csrf.js';
import { findActiveSession, parseCookies, SESSION_COOKIE_NAME } from '../security/session.js';

export function attachSession(req, db, config) {
  const cookies = parseCookies(req.headers.cookie);
  const rawToken = cookies[SESSION_COOKIE_NAME];
  req.rawSessionToken = rawToken || null;
  req.session = rawToken ? findActiveSession(db, config, rawToken) : null;
  req.user = req.session ? req.session.user : null;
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
