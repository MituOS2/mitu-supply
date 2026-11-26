import crypto from 'node:crypto';
import path from 'node:path';
import { PROJECT_ROOT } from '../config.js';
import { hashPassword } from '../security/password.js';
import { recordAudit } from '../utils/audit.js';
import { withTransaction } from './connection.js';
import { copySeedAssetToUploads, ensureSeedPngAsset } from './pngAssets.js';

const SEED_CATEGORIES = [
  {
    name: 'Hardware & Teclados',
    slug: 'hardware-teclados',
    description: 'Periféricos táctiles y cables que sobreviven a cualquier entrega final.',
    accentHex: '#3482FF',
    sortOrder: 1,
  },
  {
    name: 'Desk Setup',
    slug: 'desk-setup',
    description: 'Iluminación, deskmats y hubs para programar a las 2:00 AM.',
    accentHex: '#00BFA5',
    sortOrder: 2,
  },
  {
    name: 'Apparel & Drip',
    slug: 'apparel-drip',
    description: 'Prendas heavyweight y accesorios con humor seco de ingeniería.',
    accentHex: '#7C4DFF',
    sortOrder: 3,
  },
  {
    name: 'Edición 04',
    slug: 'edicion-04',
    description: 'Curaduría limitada firmada por Enrique, Edgarvis, Leonel y Aníbal.',
    accentHex: '#FF6D00',
    sortOrder: 4,
  },
];

const SEED_PRODUCTS = [
  {
    categorySlug: 'hardware-teclados',
    founderTag: 'Enrique',
    name: 'Teclado 65% "Ctrl+Z Físico"',
    slug: 'teclado-65-ctrl-z',
    subtitle: 'Switches táctiles silenciosos + tecla Esc color alerta',
    description: 'Diseñado por Enrique para cuando borras la base de datos por accidente. Cuerpo compacto de aluminio cepillado, keycaps PBT y retroceso emocional inmediato.',
    priceCents: 8900,
    compareAtCents: 10900,
    stock: 14,
    assetFile: 'prod-keyboard.png',
    assetKind: 'keyboard',
    accentHex: '#3482FF',
  },
  {
    categorySlug: 'hardware-teclados',
    founderTag: 'Enrique',
    name: 'Cable USB-C Blindado Anti-Parciales',
    slug: 'cable-usbc-blindado',
    subtitle: 'Trenzado de nylon 100W PD (no hace falsos contactos)',
    description: 'No requiere doblarlo en ángulo de 47 grados para cargar. Soporta datos a alta velocidad y cargas rápidas antes de entrar a clase.',
    priceCents: 1450,
    compareAtCents: 1900,
    stock: 38,
    assetFile: 'prod-cable.png',
    assetKind: 'cable',
    accentHex: '#2979FF',
  },
  {
    categorySlug: 'edicion-04',
    founderTag: 'Enrique',
    name: 'Keycap Metálica "ESC" XL',
    slug: 'keycap-esc-xl',
    subtitle: 'Aleación anodizada CNC para salidas de emergencia',
    description: 'Un toque firme cuando el profesor pregunta "¿quién quiere pasar a explicar el diagrama?". Compatible con switches estilo MX.',
    priceCents: 1900,
    compareAtCents: null,
    stock: 22,
    assetFile: 'prod-keycap.png',
    assetKind: 'keycap',
    accentHex: '#FF5252',
  },
  {
    categorySlug: 'desk-setup',
    founderTag: 'Edgarvis',
    name: 'Lámpara Barra "2:00 AM Focus"',
    slug: 'lampara-2am-focus',
    subtitle: 'Temperatura regulable 2700K-6500K sin reflejo en monitor',
    description: 'Curada por Edgarvis para sesiones nocturnas de código sin destruir tus ojos ni iluminar toda la habitación.',
    priceCents: 3900,
    compareAtCents: 4900,
    stock: 19,
    assetFile: 'prod-lamp.png',
    assetKind: 'lamp',
    accentHex: '#00BFA5',
  },
  {
    categorySlug: 'desk-setup',
    founderTag: 'Edgarvis',
    name: 'Deskmat Topográfico "Dark Compile"',
    slug: 'deskmat-dark-compile',
    subtitle: '900x400mm microtejido impermeable con bordes cosidos',
    description: 'Líneas topográficas minimalistas que disimulan manchas de café y mejoran el deslizamiento del mouse.',
    priceCents: 2499,
    compareAtCents: 3200,
    stock: 27,
    assetFile: 'prod-deskmat.png',
    assetKind: 'deskmat',
    accentHex: '#00ACC1',
  },
  {
    categorySlug: 'hardware-teclados',
    founderTag: 'Edgarvis',
    name: 'Hub USB-C 7-en-1 Minimal',
    slug: 'hub-usbc-minimal',
    subtitle: 'HDMI 4K, 2x USB-A, SD/TF y carga passthrough 100W',
    description: 'Porque tu laptop moderna decidió que dos puertos eran suficientes y claramente no lo son.',
    priceCents: 3400,
    compareAtCents: null,
    stock: 16,
    assetFile: 'prod-hub.png',
    assetKind: 'hub',
    accentHex: '#26A69A',
  },
  {
    categorySlug: 'apparel-drip',
    founderTag: 'Leonel',
    name: 'Hoodie Heavyweight "404 Sleep Not Found"',
    slug: 'hoodie-404-sleep',
    subtitle: 'Algodón 420gsm corte boxy fit color grafito',
    description: 'Selección oficial de Leonel. Suave por dentro, estructurado por fuera, ideal para aulas con aire acondicionado polar.',
    priceCents: 5400,
    compareAtCents: 6500,
    stock: 12,
    assetFile: 'prod-hoodie.png',
    assetKind: 'hoodie',
    accentHex: '#7C4DFF',
  },
  {
    categorySlug: 'apparel-drip',
    founderTag: 'Leonel',
    name: 'Gorra Estructurada "Production Ready"',
    slug: 'gorra-production-ready',
    subtitle: 'Sarga premium bordado 3D minimalista',
    description: 'Úsala incluso cuando el código todavía está en localhost. Cierre metálico ajustable.',
    priceCents: 2200,
    compareAtCents: null,
    stock: 30,
    assetFile: 'prod-cap.png',
    assetKind: 'cap',
    accentHex: '#651FFF',
  },
  {
    categorySlug: 'apparel-drip',
    founderTag: 'Leonel',
    name: 'Tote Bag "Assets & Caffeine"',
    slug: 'tote-bag-assets',
    subtitle: 'Lona gruesa 16oz con bolsillo interno para laptop 14"',
    description: 'Para cargar cargadores, apuntes doblados y tu estabilidad emocional de fin de semestre.',
    priceCents: 1800,
    compareAtCents: 2400,
    stock: 25,
    assetFile: 'prod-tote.png',
    assetKind: 'tote',
    accentHex: '#9C27B0',
  },
  {
    categorySlug: 'edicion-04',
    founderTag: 'Aníbal',
    name: 'Termo Térmico "Cold Brew Protocol"',
    slug: 'termo-cold-brew',
    subtitle: 'Acero inoxidable doble pared 600ml (12h caliente / 24h frío)',
    description: 'Recomendado por Aníbal: mantiene tu café frío intacto desde la primera hora hasta la última exposición.',
    priceCents: 2600,
    compareAtCents: 3100,
    stock: 20,
    assetFile: 'prod-thermos.png',
    assetKind: 'thermos',
    accentHex: '#FF6D00',
  },
  {
    categorySlug: 'desk-setup',
    founderTag: 'Aníbal',
    name: 'Organizador Modular Magnético',
    slug: 'organizador-modular',
    subtitle: 'Bandeja modular para adaptadores, USBs y llaves',
    description: 'Convierte tu escritorio caótico en un anuncio de productividad en menos de treinta segundos.',
    priceCents: 3150,
    compareAtCents: null,
    stock: 15,
    assetFile: 'prod-organizer.png',
    assetKind: 'organizer',
    accentHex: '#FF9100',
  },
  {
    categorySlug: 'edicion-04',
    founderTag: 'Aníbal',
    name: 'Pack Stickers Holográficos Lote 04',
    slug: 'stickers-lote-04',
    subtitle: 'Set de 10 calcomanías vinilo resistente al agua',
    description: 'Incluye emblemas de TBD.SUPPLY, HyperOS vibes y sellos de Enrique, Edgarvis, Leonel y Aníbal para personalizar tu laptop.',
    priceCents: 950,
    compareAtCents: 1200,
    stock: 60,
    assetFile: 'prod-stickers.png',
    assetKind: 'stickers',
    accentHex: '#FF3D00',
  },
];

function generateAdmin20CharKey() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';
  const bytes = crypto.randomBytes(20);
  let out = '';
  for (let i = 0; i < 20; i += 1) {
    out += alphabet[bytes[i] % alphabet.length];
  }
  return out;
}

export async function runSeed(db, config) {
  const seedAssetsDir = path.join(PROJECT_ROOT, 'scripts', 'seed-assets');
  const now = Date.now();
  let bootstrapAdminInfo = null;

  // 1. Generate a 20-character ephemeral terminal key for admin on startup (unless fixed in tests).
  const generatedPassword = config.adminBootstrapPassword || generateAdmin20CharKey();
  const passwordHash = await hashPassword(generatedPassword);
  const existingAdmin = db.prepare("SELECT id, username FROM users WHERE role = 'admin' LIMIT 1").get();

  if (!existingAdmin) {
    db.prepare(`
      INSERT INTO users (username, email, display_name, password_hash, role, is_active, created_at, updated_at)
      VALUES (?, ?, ?, ?, 'admin', 1, ?, ?)
    `).run(
      config.adminBootstrapUser,
      config.adminBootstrapEmail,
      'Admin Escuadrón 04',
      passwordHash,
      now,
      now,
    );
    const adminRow = db.prepare('SELECT id FROM users WHERE username = ?').get(config.adminBootstrapUser);
    recordAudit(db, {
      actorUserId: adminRow?.id ?? null,
      action: 'BOOTSTRAP_ADMIN',
      targetType: 'user',
      targetId: String(adminRow?.id ?? ''),
      metadata: { username: config.adminBootstrapUser },
    });
    bootstrapAdminInfo = {
      username: config.adminBootstrapUser,
      password: generatedPassword,
      fromEnv: Boolean(config.adminBootstrapPassword),
    };
  } else {
    db.prepare(`
      UPDATE users
      SET password_hash = ?, is_active = 1, failed_login_attempts = 0, locked_until = NULL, updated_at = ?
      WHERE id = ?
    `).run(passwordHash, now, existingAdmin.id);
    bootstrapAdminInfo = {
      username: existingAdmin.username,
      password: generatedPassword,
      fromEnv: Boolean(config.adminBootstrapPassword),
    };
  }

  // 2. Seed demo categories and products ONLY when explicitly enabled (e.g. automated tests).
  const catCount = db.prepare('SELECT COUNT(*) AS cnt FROM categories').get().cnt;
  if (config.seedDemoCatalog && catCount === 0) {
    withTransaction(db, 'IMMEDIATE', () => {
      const catIdBySlug = new Map();
      const insertCat = db.prepare(`
        INSERT INTO categories (name, slug, description, accent_hex, sort_order, is_active, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, 1, ?, ?)
      `);
      for (const cat of SEED_CATEGORIES) {
        const res = insertCat.run(cat.name, cat.slug, cat.description, cat.accentHex, cat.sortOrder, now, now);
        catIdBySlug.set(cat.slug, Number(res.lastInsertRowid));
      }

      const insertProd = db.prepare(`
        INSERT INTO products (
          category_id, founder_tag, name, slug, subtitle, description,
          price_cents, compare_at_cents, stock, is_active, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
      `);
      const insertImg = db.prepare(`
        INSERT INTO product_images (
          product_id, storage_key, mime_type, byte_size, sha256, is_primary, sort_order, created_at
        ) VALUES (?, ?, ?, ?, ?, 1, 0, ?)
      `);

      for (const prod of SEED_PRODUCTS) {
        const catId = catIdBySlug.get(prod.categorySlug);
        const prodRes = insertProd.run(
          catId,
          prod.founderTag,
          prod.name,
          prod.slug,
          prod.subtitle,
          prod.description,
          prod.priceCents,
          prod.compareAtCents,
          prod.stock,
          now,
          now,
        );
        const productId = Number(prodRes.lastInsertRowid);
        const assetPath = ensureSeedPngAsset(seedAssetsDir, prod.assetFile, prod.assetKind, prod.accentHex);
        const saved = copySeedAssetToUploads(assetPath, config.uploadDir);
        insertImg.run(productId, saved.storageKey, saved.mimeType, saved.byteSize, saved.sha256, now);
      }

      recordAudit(db, {
        actorUserId: null,
        action: 'SEED_CATALOG',
        targetType: 'catalog',
        targetId: 'initial',
        metadata: { categories: SEED_CATEGORIES.length, products: SEED_PRODUCTS.length },
      });
    });
  }

  return { bootstrapAdminInfo };
}
