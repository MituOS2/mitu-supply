import { HttpError } from '../http/io.js';

export function requireAuth(req) {
  if (!req.user || !req.session) {
    throw new HttpError(401, 'Debes iniciar sesion para continuar');
  }
}

export function requireAdmin(req) {
  requireAuth(req);
  if (req.user.role !== 'admin') {
    throw new HttpError(403, 'Acceso restringido a administradores');
  }
}
