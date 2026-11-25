import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

let serverInstance = null;

async function getServer() {
  if (serverInstance) return serverInstance;
  const { createAppServer } = await import('../server/src/app.js');
  const { loadConfig } = await import('../server/src/config.js');
  const { openDatabase } = await import('../server/src/db/connection.js');
  const { runMigrations } = await import('../server/src/db/migrations.js');
  const { runSeed } = await import('../server/src/db/seed.js');

  const tmpDbPath = path.join(os.tmpdir(), 'store.sqlite');
  const tmpUploadDir = path.join(os.tmpdir(), 'uploads');
  fs.mkdirSync(tmpUploadDir, { recursive: true });

  const config = loadConfig({
    dbPath: tmpDbPath,
    uploadDir: tmpUploadDir,
    seedDemoCatalog: true,
    localAutoAdmin: false,
  });

  const db = openDatabase(config.dbPath);
  runMigrations(db);
  await runSeed(db, config);

  const { server } = createAppServer({ db, config });
  serverInstance = server;
  return serverInstance;
}

export default async function handler(req, res) {
  try {
    const server = await getServer();
    server.emit('request', req, res);
  } catch (err) {
    console.error('[vercel-api-error]', err);
    res.statusCode = 500;
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify({ error: err.message || 'Error inicializando el servidor Vercel' }));
  }
}
