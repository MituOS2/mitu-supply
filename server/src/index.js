import fs from 'node:fs';
import { createAppServer } from './app.js';
import { loadConfig } from './config.js';
import { openDatabase } from './db/connection.js';
import { runMigrations } from './db/migrations.js';
import { runSeed } from './db/seed.js';

const config = loadConfig();
fs.mkdirSync(config.uploadDir, { recursive: true });

const db = openDatabase(config.dbPath);
runMigrations(db);
const { bootstrapAdminInfo } = await runSeed(db, config);

const { server } = createAppServer({ db, config });

server.listen(config.port, config.host, () => {
  const addr = server.address();
  const port = typeof addr === 'object' && addr ? addr.port : config.port;
  console.log(`[${config.brandName}] Servidor escuchando en http://${config.host}:${port} (Localhost tiene Admin automatico)`);
});

let closing = false;
function shutdown(signal) {
  if (closing) return;
  closing = true;
  console.log(`[${config.brandName}] Cerrando por ${signal}...`);
  server.close(() => {
    try {
      db.close();
    } finally {
      process.exit(0);
    }
  });
  setTimeout(() => process.exit(1), 5000).unref();
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
