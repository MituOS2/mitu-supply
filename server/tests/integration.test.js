import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { createAppServer } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { openDatabase } from '../src/db/connection.js';
import { runMigrations } from '../src/db/migrations.js';
import { runSeed } from '../src/db/seed.js';

function createTinyPngBuffer() {
  // Minimal valid 1x1 transparent PNG
  return Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
    'base64',
  );
}

class TestClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
    this.cookie = '';
    this.csrfToken = '';
  }

  async request(method, reqPath, { json = null, rawBody = null, headers = {} } = {}) {
    const reqHeaders = { ...headers };
    if (this.cookie) reqHeaders.Cookie = this.cookie;
    if (this.csrfToken && method !== 'GET' && !reqHeaders['X-CSRF-Token']) {
      reqHeaders['X-CSRF-Token'] = this.csrfToken;
    }
    if (!reqHeaders.Origin && method !== 'GET') {
      reqHeaders.Origin = this.baseUrl;
    }

    let body = rawBody;
    if (json !== null) {
      reqHeaders['Content-Type'] = 'application/json';
      body = JSON.stringify(json);
    }

    const res = await fetch(`${this.baseUrl}${reqPath}`, {
      method,
      headers: reqHeaders,
      body,
    });

    const setCookie = res.headers.get('set-cookie');
    if (setCookie) {
      const pair = setCookie.split(';')[0];
      if (pair.endsWith('=')) {
        this.cookie = '';
      } else {
        this.cookie = pair;
      }
    }

    const ct = res.headers.get('content-type') || '';
    const data = ct.includes('application/json') ? await res.json() : Buffer.from(await res.arrayBuffer());
    if (data && typeof data === 'object' && !Buffer.isBuffer(data) && data.csrfToken) {
      this.csrfToken = data.csrfToken;
    }

    return { status: res.status, headers: res.headers, data };
  }
}

async function setupTestHarness() {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tbd-int-'));
  const config = loadConfig({
    port: 0,
    host: '127.0.0.1',
    dbPath: path.join(tmpDir, 'test.sqlite'),
    uploadDir: path.join(tmpDir, 'uploads'),
    distWebDir: path.join(tmpDir, 'dist-web'),
    adminBootstrapPassword: 'AdminStrongPass2026!',
    seedDemoCatalog: true,
    localAutoAdmin: false,
  });

  const db = openDatabase(config.dbPath);
  runMigrations(db);
  await runSeed(db, config);

  const { server } = createAppServer({ db, config });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  return {
    baseUrl,
    db,
    config,
    cleanup: async () => {
      await new Promise((resolve) => server.close(resolve));
      db.close();
      fs.rmSync(tmpDir, { recursive: true, force: true });
    },
  };
}

test('Flujo integral completo: catalogo, registro, carrito, compra atomica y panel admin', async () => {
  const h = await setupTestHarness();
  try {
    const guest = new TestClient(h.baseUrl);

    // 1. Health + Cabeceras de seguridad
    const health = await guest.request('GET', '/api/health');
    assert.equal(health.status, 200);
    assert.equal(health.data.ok, true);
    assert.equal(health.headers.get('x-frame-options'), 'DENY');
    assert.equal(health.headers.get('x-content-type-options'), 'nosniff');
    assert.equal(health.headers.get('x-powered-by'), null);

    // 2. Categorias y productos semilla + imagen PNG real
    const catsRes = await guest.request('GET', '/api/categories');
    assert.equal(catsRes.status, 200);
    assert.ok(catsRes.data.categories.length >= 4);

    const prodsRes = await guest.request('GET', '/api/products');
    assert.equal(prodsRes.status, 200);
    assert.ok(prodsRes.data.items.length >= 12);

    const firstProd = prodsRes.data.items[0];
    assert.ok(firstProd.primaryImageUrl);
    const mediaRes = await guest.request('GET', firstProd.primaryImageUrl);
    assert.equal(mediaRes.status, 200);
    assert.equal(mediaRes.headers.get('content-type'), 'image/png');
    assert.equal(mediaRes.data[0], 0x89);
    assert.equal(mediaRes.data[1], 0x50);

    // 3. Registro simple de cliente (3 campos) y compra atomica
    const simpleUser = new TestClient(h.baseUrl);
    const simpleReg = await simpleUser.request('POST', '/api/auth/register', {
      json: {
        username: 'LeoSimple',
        email: 'leosimple@example.com',
        password: 'clave123',
      },
    });
    assert.equal(simpleReg.status, 201);
    assert.equal(simpleReg.data.user.username, 'leosimple');
    assert.equal(simpleReg.data.user.displayName, 'LeoSimple');
    assert.equal(simpleReg.data.user.role, 'customer');

    const buyer = new TestClient(h.baseUrl);
    const reg = await buyer.request('POST', '/api/auth/register', {
      json: {
        username: 'enrique_test',
        email: 'enrique@example.com',
        displayName: 'Enrique Dev',
        password: 'Password2026!',
        role: 'admin', // Debe ser ignorado por el servidor
      },
    });
    assert.equal(reg.status, 201);
    assert.equal(reg.data.user.role, 'customer');

    const initialStock = firstProd.stock;
    const addCart = await buyer.request('PUT', `/api/cart/items/${firstProd.id}`, {
      json: { quantity: 2 },
    });
    assert.equal(addCart.status, 200);
    assert.equal(addCart.data.itemCount, 2);
    assert.equal(addCart.data.subtotalCents, firstProd.priceCents * 2);

    const checkout = await buyer.request('POST', '/api/checkout', {
      json: {
        shippingName: 'Enrique Salon 4',
        shippingNote: 'Entregar antes del parcial',
      },
    });
    assert.equal(checkout.status, 201);
    assert.equal(checkout.data.order.totalCents, firstProd.priceCents * 2);
    assert.equal(checkout.data.order.items[0].productName, firstProd.name);
    const orderCode = checkout.data.order.orderCode;

    // Verificar descuento atomico en SQLite
    const afterProd = await guest.request('GET', `/api/products/${firstProd.slug}`);
    assert.equal(afterProd.data.product.stock, initialStock - 2);

    // Verificar mis pedidos
    const myOrders = await buyer.request('GET', '/api/orders');
    assert.equal(myOrders.status, 200);
    assert.equal(myOrders.data.orders.length, 1);
    assert.equal(myOrders.data.orders[0].orderCode, orderCode);

    // 4. Flujo completo de Administrador
    const admin = new TestClient(h.baseUrl);
    const adminLogin = await admin.request('POST', '/api/auth/login', {
      json: { identifier: 'admin', password: 'AdminStrongPass2026!' },
    });
    assert.equal(adminLogin.status, 200);
    assert.equal(adminLogin.data.user.role, 'admin');

    const dash = await admin.request('GET', '/api/admin/dashboard');
    assert.equal(dash.status, 200);
    assert.ok(dash.data.summary.revenueCents >= firstProd.priceCents * 2);

    const newCat = await admin.request('POST', '/api/admin/categories', {
      json: {
        name: 'Audio & Focus',
        slug: 'audio-focus',
        description: 'Audifonos y silencio',
        accentHex: '#FF4081',
        sortOrder: 9,
      },
    });
    assert.equal(newCat.status, 201);

    const newProd = await admin.request('POST', '/api/admin/products', {
      json: {
        categoryId: newCat.data.category.id,
        founderTag: 'Edgarvis',
        name: 'Tapones Anti-Spoiler',
        slug: 'tapones-anti-spoiler',
        subtitle: 'Aislamiento acustico para entregas',
        description: 'Bloquea discusiones de arquitectura en el pasillo.',
        priceCents: 1299,
        stock: 5,
        isActive: true,
      },
    });
    assert.equal(newProd.status, 201);

    // Subida multipart real de imagen PNG
    const boundary = '----TBDTestBoundary12345';
    const png = createTinyPngBuffer();
    const multipartBody = Buffer.concat([
      Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="productId"\r\n\r\n${newProd.data.product.id}\r\n` +
          `--${boundary}\r\nContent-Disposition: form-data; name="isPrimary"\r\n\r\ntrue\r\n` +
          `--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="cover.png"\r\nContent-Type: image/png\r\n\r\n`,
        'utf8',
      ),
      png,
      Buffer.from(`\r\n--${boundary}--\r\n`, 'utf8'),
    ]);

    const uploadRes = await admin.request('POST', '/api/admin/media', {
      rawBody: multipartBody,
      headers: { 'Content-Type': `multipart/form-data; boundary=${boundary}` },
    });
    assert.equal(uploadRes.status, 201);
    assert.ok(uploadRes.data.image.url.startsWith('/api/media/'));

    // Creacion rapida de producto escribiendo nombre de categoria ("Leonel") sin crearla antes
    const directCatProd = await admin.request('POST', '/api/admin/products', {
      json: {
        categoryName: 'Leonel',
        name: 'Gorra Leonel Custom',
        priceCents: 1500,
        stock: 8,
      },
    });
    assert.equal(directCatProd.status, 201);
    assert.equal(directCatProd.data.product.founderTag, 'Leonel');

    // Editar producto enviando slug: null y categoryId: null (como serializa Kotlin/Wasm)
    const patchProd = await admin.request('PATCH', `/api/admin/products/${directCatProd.data.product.id}`, {
      json: {
        categoryId: null,
        categoryName: 'Leonel',
        name: 'Gorra Leonel Editada',
        slug: null,
        priceCents: 1800,
        compareAtCents: null,
        stock: 12,
        isActive: true,
      },
    });
    assert.equal(patchProd.status, 200);
    assert.equal(patchProd.data.product.name, 'Gorra Leonel Editada');
    assert.equal(patchProd.data.product.priceCents, 1800);

    // Eliminar categoria con productos asignados funciona directamente sin bloqueo 409
    const delCatRes = await admin.request('DELETE', `/api/admin/categories/${directCatProd.data.product.categoryId}`);
    assert.equal(delCatRes.status, 200);

    // Chat dentro de la orden: cliente envia texto y admin responde con texto + imagen PNG
    const custMsg = await buyer.request('POST', `/api/orders/${orderCode}/messages`, {
      json: { message: 'Hola, envio en puerta principal porfa' },
    });
    assert.equal(custMsg.status, 201);
    assert.equal(custMsg.data.message.senderRole, 'customer');
    assert.equal(custMsg.data.message.imageUrl, null);

    const chatBoundary = '----TBDChatBoundary987';
    const chatMultipart = Buffer.concat([
      Buffer.from(
        `--${chatBoundary}\r\nContent-Disposition: form-data; name="message"\r\n\r\nListo, aqui tienes la foto del paquete\r\n` +
          `--${chatBoundary}\r\nContent-Disposition: form-data; name="image"; filename="paquete.png"\r\nContent-Type: image/png\r\n\r\n`,
        'utf8',
      ),
      png,
      Buffer.from(`\r\n--${chatBoundary}--\r\n`, 'utf8'),
    ]);

    const adminChatMsg = await admin.request('POST', `/api/orders/${orderCode}/messages`, {
      rawBody: chatMultipart,
      headers: { 'Content-Type': `multipart/form-data; boundary=${chatBoundary}` },
    });
    assert.equal(adminChatMsg.status, 201);
    assert.equal(adminChatMsg.data.message.senderRole, 'admin');
    assert.ok(adminChatMsg.data.message.imageUrl.startsWith(`/api/orders/${orderCode}/attachments/`));

    const threadRes = await buyer.request('GET', `/api/orders/${orderCode}/messages`);
    assert.equal(threadRes.status, 200);
    assert.equal(threadRes.data.messages.length, 2);
    assert.equal(threadRes.data.messages[0].messageText, 'Hola, envio en puerta principal porfa');
    assert.equal(threadRes.data.messages[1].messageText, 'Listo, aqui tienes la foto del paquete');

    const attRes = await buyer.request('GET', adminChatMsg.data.message.imageUrl);
    assert.equal(attRes.status, 200);
    assert.equal(attRes.headers.get('content-type'), 'image/png');
    assert.equal(attRes.data[0], 0x89);

    const adminOrders = await admin.request('GET', '/api/admin/orders');
    assert.equal(adminOrders.status, 200);
    assert.ok(adminOrders.data.orders.some((o) => o.orderCode === orderCode && o.messageCount === 2));

    // Cancelar orden y verificar restauracion de stock atomica
    const cancelRes = await admin.request('PATCH', `/api/admin/orders/${checkout.data.order.id}/status`, {
      json: { status: 'cancelled' },
    });
    assert.equal(cancelRes.status, 200);
    const restoredProd = await guest.request('GET', `/api/products/${firstProd.slug}`);
    assert.equal(restoredProd.data.product.stock, initialStock);

    // Revocar sesiones del cliente desde Admin
    const revokeRes = await admin.request('POST', `/api/admin/users/${reg.data.user.id}/revoke-sessions`, {
      json: {},
    });
    assert.equal(revokeRes.status, 200);
    assert.ok(revokeRes.data.revoked >= 1);

    const meAfterRevoke = await buyer.request('GET', '/api/auth/me');
    assert.equal(meAfterRevoke.data.user, null);
  } finally {
    await h.cleanup();
  }
});
