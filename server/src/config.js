import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
export const SERVER_ROOT = path.resolve(__dirname, '..');
export const PROJECT_ROOT = path.resolve(SERVER_ROOT, '..');

function loadDotEnv(envPath) {
  if (!fs.existsSync(envPath)) return;
  const raw = fs.readFileSync(envPath, 'utf8');
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eqIndex = trimmed.indexOf('=');
    if (eqIndex <= 0) continue;
    const key = trimmed.slice(0, eqIndex).trim();
    let val = trimmed.slice(eqIndex + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    if (!(key in process.env) || process.env[key] === '') {
      process.env[key] = val;
    }
  }
}

export function loadConfig(overrides = {}) {
  loadDotEnv(path.join(PROJECT_ROOT, '.env'));

  const portRaw = overrides.port ?? process.env.PORT ?? '3000';
  const port = Number.parseInt(String(portRaw), 10);
  if (!Number.isInteger(port) || port < 0 || port > 65535) {
    throw new Error(`Invalid PORT configuration: ${portRaw}`);
  }

  const host = String(overrides.host ?? process.env.HOST ?? '127.0.0.1').trim() || '127.0.0.1';
  const nodeEnv = String(overrides.nodeEnv ?? process.env.NODE_ENV ?? 'development').trim();
  const isProd = nodeEnv === 'production';

  const dbPath = path.resolve(
    PROJECT_ROOT,
    String(overrides.dbPath ?? (process.env.DB_PATH || 'server/data/store.sqlite')),
  );
  const uploadDir = path.resolve(
    PROJECT_ROOT,
    String(overrides.uploadDir ?? (process.env.UPLOAD_DIR || 'server/storage/uploads')),
  );
  const distWebDir = path.resolve(
    PROJECT_ROOT,
    String(overrides.distWebDir ?? (process.env.DIST_WEB_DIR || 'server/dist-web')),
  );

  // Ephemeral fallback only for non-persisted test runs; production/bootstrap writes .env.
  const sessionPepper =
    String(overrides.sessionPepper ?? process.env.SESSION_PEPPER ?? '').trim() ||
    crypto.randomBytes(32).toString('hex');
  const ipHashSalt =
    String(overrides.ipHashSalt ?? process.env.IP_HASH_SALT ?? '').trim() ||
    crypto.randomBytes(32).toString('hex');

  const allowedOrigins = String(process.env.ALLOWED_ORIGINS || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);

  return {
    port,
    host,
    nodeEnv,
    isProd,
    dbPath,
    uploadDir,
    distWebDir,
    sessionPepper,
    ipHashSalt,
    allowedOrigins,
    trustProxy: process.env.TRUST_PROXY === 'true',
    cookieSecure: process.env.COOKIE_SECURE === 'true' || isProd,
    sessionTtlMs: 1000 * 60 * 60 * 24 * 7, // 7 dias maximos
    sessionIdleTtlMs: 1000 * 60 * 60 * 12, // 12 horas de inactividad
    maxJsonBytes: 256 * 1024, // 256 KB
    maxUploadBytes: 3 * 1024 * 1024, // 3 MB
    brandName: 'TBD.SUPPLY',
    seedDemoCatalog: overrides.seedDemoCatalog ?? (process.env.SEED_DEMO_CATALOG === 'true'),
    localAutoAdmin: overrides.localAutoAdmin ?? (process.env.LOCAL_AUTO_ADMIN !== 'false'),
    adminBootstrapUser: String(overrides.adminBootstrapUser ?? (process.env.ADMIN_BOOTSTRAP_USER || 'admin')).trim(),
    adminBootstrapEmail: String(overrides.adminBootstrapEmail ?? (process.env.ADMIN_BOOTSTRAP_EMAIL || 'admin@tbdsupply.local')).trim(),
    adminBootstrapPassword: String(overrides.adminBootstrapPassword ?? (process.env.ADMIN_BOOTSTRAP_PASSWORD || '')),
  };
}
