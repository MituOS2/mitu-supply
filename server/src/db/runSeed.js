import { loadConfig } from '../config.js';
import { openDatabase } from './connection.js';
import { runMigrations } from './migrations.js';
import { runSeed } from './seed.js';

const config = loadConfig();
const db = openDatabase(config.dbPath);
try {
  runMigrations(db);
  const { bootstrapAdminInfo } = await runSeed(db, config);
  console.log('[seed] Base de datos SQLite lista en:', config.dbPath);
  if (bootstrapAdminInfo) {
    console.log(
      `[seed] Cuenta Admin inicial creada -> usuario: ${bootstrapAdminInfo.username} | clave: ${bootstrapAdminInfo.password}`,
    );
  }
} finally {
  db.close();
}
