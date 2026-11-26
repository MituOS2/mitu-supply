import crypto from 'node:crypto';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { HttpError, sendJson } from './http/io.js';
import { Router } from './http/router.js';
import { attachSession } from './middleware/auth.js';
import { registerAdminRoutes } from './routes/admin.routes.js';
import { registerAuthRoutes } from './routes/auth.routes.js';
import { registerCartRoutes } from './routes/cart.routes.js';
import { registerOrderRoutes } from './routes/orders.routes.js';
import { registerStoreRoutes } from './routes/store.routes.js';
import { applySecurityHeaders } from './security/headers.js';
import { SlidingWindowLimiter } from './security/rateLimit.js';

const STATIC_MIME = Object.freeze({
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.wasm': 'application/wasm',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.woff2': 'font/woff2',
  '.ico': 'image/x-icon',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.txt': 'text/plain; charset=utf-8',
});

function serveStaticWeb(req, res, pathname, distWebDir) {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    throw new HttpError(405, 'Metodo no permitido');
  }

  let decodedPath;
  try {
    decodedPath = decodeURIComponent(pathname);
  } catch {
    throw new HttpError(400, 'URL invalida');
  }

  if (decodedPath.includes('\0')) {
    throw new HttpError(400, 'URL invalida');
  }

  const distRoot = path.resolve(distWebDir);
  if (!fs.existsSync(distRoot)) {
    res.writeHead(503, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(
      '<!doctype html><html lang="es"><body style="font-family:sans-serif;padding:40px"><h1>Interfaz web no compilada aun</h1><p>Ejecuta <code>iniciar.bat</code> para compilar la interfaz WebAssembly.</p></body></html>',
    );
    return;
  }

  const rel = decodedPath === '/' ? 'index.html' : decodedPath.replace(/^\/+/, '');
  let targetPath = path.resolve(distRoot, rel);
  if (targetPath !== distRoot && !targetPath.startsWith(distRoot + path.sep)) {
    throw new HttpError(404, 'No encontrado');
  }

  const hasExtension = path.posix.basename(rel).includes('.');
  if (!fs.existsSync(targetPath) || fs.statSync(targetPath).isDirectory()) {
    if (hasExtension) {
      throw new HttpError(404, 'Archivo no encontrado');
    }
    targetPath = path.join(distRoot, 'index.html');
    if (!fs.existsSync(targetPath)) {
      throw new HttpError(404, 'index.html no encontrado');
    }
  }

  const ext = path.extname(targetPath).toLowerCase();
  const mimeType = STATIC_MIME[ext] || 'application/octet-stream';
  const stat = fs.statSync(targetPath);

  const fileName = path.basename(targetPath).toLowerCase();
  const cacheControl =
    ext === '.html' ||
    ext === '.wasm' ||
    ext === '.js' ||
    ext === '.mjs' ||
    ext === '.css' ||
    fileName.includes('composeapp')
      ? 'no-cache, must-revalidate'
      : 'public, max-age=3600';

  res.writeHead(200, {
    'Content-Type': mimeType,
    'Content-Length': String(stat.size),
    'Cache-Control': cacheControl,
  });

  if (req.method === 'HEAD') {
    res.end();
    return;
  }
  fs.createReadStream(targetPath).pipe(res);
}

export function createAppServer({ db, config }) {
  const router = new Router();
  const limiter = new SlidingWindowLimiter();

  registerStoreRoutes(router, { db, config, limiter });
  registerAuthRoutes(router, { db, config, limiter });
  registerCartRoutes(router, { db, config, limiter });
  registerOrderRoutes(router, { db, config, limiter });
  registerAdminRoutes(router, { db, config, limiter });

  const server = http.createServer(async (req, res) => {
    const requestId = crypto.randomBytes(6).toString('hex');
    applySecurityHeaders(res, { isMedia: false });

    try {
      const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
      const pathname = url.pathname;

      if (pathname.startsWith('/api/')) {
        attachSession(req, db, config);
        const match = router.match(req.method || 'GET', pathname);
        if (!match) {
          return sendJson(res, 404, { error: 'Ruta de API no encontrada' });
        }
        await match.handler(req, res, url, match.params);
        return;
      }

      serveStaticWeb(req, res, pathname, config.distWebDir);
    } catch (err) {
      if (res.headersSent) {
        res.destroy();
        return;
      }
      if (err instanceof HttpError) {
        return sendJson(res, err.status, { error: err.message }, err.headers);
      }
      console.error(`[error:${requestId}]`, err);
      return sendJson(res, 500, { error: 'Error interno del servidor', requestId });
    }
  });

  server.requestTimeout = 30_000;
  server.headersTimeout = 15_000;
  server.keepAliveTimeout = 10_000;

  return { server, router, limiter };
}
