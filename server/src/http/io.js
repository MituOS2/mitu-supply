import { sanitizePlainObject, ValidationError } from '../security/validate.js';

export class HttpError extends Error {
  constructor(status, message, headers = {}) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.headers = headers;
  }
}

export function getClientIp(req, config) {
  if (config.trustProxy) {
    const xff = req.headers['x-forwarded-for'];
    if (typeof xff === 'string' && xff.trim()) {
      return xff.split(',')[0].trim();
    }
  }
  return req.socket?.remoteAddress || '127.0.0.1';
}

export function readRawBody(req, maxBytes) {
  return new Promise((resolve, reject) => {
    const contentLength = Number.parseInt(req.headers['content-length'] || '0', 10);
    if (Number.isFinite(contentLength) && contentLength > maxBytes) {
      reject(new HttpError(413, 'El cuerpo de la peticion excede el tamano maximo'));
      req.resume();
      return;
    }

    const chunks = [];
    let total = 0;
    let settled = false;

    const fail = (err) => {
      if (settled) return;
      settled = true;
      reject(err);
    };

    req.on('data', (chunk) => {
      total += chunk.length;
      if (total > maxBytes) {
        req.pause();
        fail(new HttpError(413, 'El cuerpo de la peticion excede el tamano maximo'));
        return;
      }
      chunks.push(chunk);
    });

    req.on('end', () => {
      if (settled) return;
      settled = true;
      resolve(Buffer.concat(chunks));
    });

    req.on('error', (err) => fail(err));
    req.on('aborted', () => fail(new HttpError(400, 'Conexion interrumpida antes de completar el envio')));
  });
}

export async function readJsonBody(req, maxBytes) {
  const ct = String(req.headers['content-type'] || '').toLowerCase();
  if (!ct.includes('application/json')) {
    throw new HttpError(415, 'Se requiere Content-Type: application/json');
  }

  const buf = await readRawBody(req, maxBytes);
  if (buf.length === 0) return Object.create(null);

  let parsed;
  try {
    parsed = JSON.parse(buf.toString('utf8'));
  } catch {
    throw new HttpError(400, 'JSON malformado');
  }

  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new HttpError(400, 'El cuerpo JSON debe ser un objeto');
  }

  try {
    return sanitizePlainObject(parsed);
  } catch (err) {
    if (err instanceof ValidationError) {
      throw new HttpError(400, err.message);
    }
    throw err;
  }
}

export function sendJson(res, status, payload, extraHeaders = {}) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    ...extraHeaders,
  });
  res.end(body);
}
