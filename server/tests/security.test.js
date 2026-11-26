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

async function setupSecurityHarness() {
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'tbd-sec-'));
  const config = loadConfig({
    port: 0,
    host: '127.0.0.1',
    dbPath: path.join(tmpDir, 'sec.sqlite'),
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

async function registerCustomer(baseUrl, username, email) {
  const res = await fetch(`${baseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Origin: baseUrl },
    body: JSON.stringify({
      username,
      email,
      displayName: username,
      password: 'UserPass2026!',
    }),
  });
  const data = await res.json();
  const cookie = res.headers.get('set-cookie').split(';')[0];
  return { cookie, csrfToken: data.csrfToken, user: data.user, setCookieHeader: res.headers.get('set-cookie') };
}

test('Bateria de seguridad: SQLi, CSRF, RBAC, IDOR, SVG XSS, Path Traversal, Brute Force y Race Condition', async () => {
  const h = await setupSecurityHarness();
  try {
    // 1. SQL Injection en busqueda y sort
    const sqliSearch = await fetch(
      `${h.baseUrl}/api/products?q=${encodeURIComponent("' OR 1=1 --")}&sort=${encodeURIComponent('price_cents; DROP TABLE users;--')}`,
    );
    assert.equal(sqliSearch.status, 200);
    const sqliData = await sqliSearch.json();
    assert.equal(sqliData.items.length, 0);

    // 2. Registro e inspeccion de Cookie + Sesion Hashed en SQLite
    const alice = await registerCustomer(h.baseUrl, 'alice_sec', 'alice@example.com');
    const bob = await registerCustomer(h.baseUrl, 'bob_sec', 'bob@example.com');
    assert.match(alice.setCookieHeader, /HttpOnly/i);
    assert.match(alice.setCookieHeader, /SameSite=Strict/i);

    const rawCookieVal = decodeURIComponent(alice.cookie.split('=')[1]);
    const rawInDb = h.db.prepare('SELECT token_hash FROM sessions WHERE token_hash = ?').get(rawCookieVal);
    assert.equal(rawInDb, undefined, 'El token plano de sesion nunca debe guardarse en SQLite');

    // 3. Proteccion CSRF (sin token, token falso, origen externo)
    const noCsrf = await fetch(`${h.baseUrl}/api/cart/items/1`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Cookie: alice.cookie,
        Origin: h.baseUrl,
      },
      body: JSON.stringify({ quantity: 1 }),
    });
    assert.equal(noCsrf.status, 403);

    const evilOrigin = await fetch(`${h.baseUrl}/api/cart/items/1`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Cookie: alice.cookie,
        'X-CSRF-Token': alice.csrfToken,
        Origin: 'https://evil-attacker.example',
      },
      body: JSON.stringify({ quantity: 1 }),
    });
    assert.equal(evilOrigin.status, 403);

    // 4. Escalada de privilegios (RBAC)
    const custAdminDash = await fetch(`${h.baseUrl}/api/admin/dashboard`, {
      headers: { Cookie: alice.cookie },
    });
    assert.equal(custAdminDash.status, 403);

    const anonAdminDash = await fetch(`${h.baseUrl}/api/admin/dashboard`);
    assert.equal(anonAdminDash.status, 401);

    // 5. Anti-IDOR en ordenes
    await fetch(`${h.baseUrl}/api/cart/items/1`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Cookie: alice.cookie,
        'X-CSRF-Token': alice.csrfToken,
        Origin: h.baseUrl,
      },
      body: JSON.stringify({ quantity: 1 }),
    });
    const aliceCheckout = await fetch(`${h.baseUrl}/api/checkout`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Cookie: alice.cookie,
        'X-CSRF-Token': alice.csrfToken,
        Origin: h.baseUrl,
      },
      body: JSON.stringify({ shippingName: 'Alice' }),
    });
    assert.equal(aliceCheckout.status, 201);
    const aliceOrder = (await aliceCheckout.json()).order;

    const bobSpy = await fetch(`${h.baseUrl}/api/orders/${aliceOrder.orderCode}`, {
      headers: { Cookie: bob.cookie },
    });
    assert.equal(bobSpy.status, 404);

    // 5b. Anti-IDOR, SVG XSS y autenticacion en Chat de Ordenes + Adjuntos
    const validPng = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
      'base64',
    );
    const aliceBoundary = '----AliceChatPng';
    const aliceImgPayload = Buffer.concat([
      Buffer.from(
        `--${aliceBoundary}\r\nContent-Disposition: form-data; name="message"\r\n\r\nComprobante\r\n` +
          `--${aliceBoundary}\r\nContent-Disposition: form-data; name="image"; filename="recibo.png"\r\nContent-Type: image/png\r\n\r\n`,
      ),
      validPng,
      Buffer.from(`\r\n--${aliceBoundary}--\r\n`),
    ]);
    const aliceChatPost = await fetch(`${h.baseUrl}/api/orders/${aliceOrder.orderCode}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': `multipart/form-data; boundary=${aliceBoundary}`,
        Cookie: alice.cookie,
        'X-CSRF-Token': alice.csrfToken,
        Origin: h.baseUrl,
      },
      body: aliceImgPayload,
    });
    assert.equal(aliceChatPost.status, 201);
    const aliceChatData = await aliceChatPost.json();
    const attachmentUrl = aliceChatData.message.imageUrl;
    assert.ok(attachmentUrl);

    const bobChatRead = await fetch(`${h.baseUrl}/api/orders/${aliceOrder.orderCode}/messages`, {
      headers: { Cookie: bob.cookie },
    });
    assert.equal(bobChatRead.status, 404);

    const bobChatWrite = await fetch(`${h.baseUrl}/api/orders/${aliceOrder.orderCode}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Cookie: bob.cookie,
        'X-CSRF-Token': bob.csrfToken,
        Origin: h.baseUrl,
      },
      body: JSON.stringify({ message: 'Intento intruso' }),
    });
    assert.equal(bobChatWrite.status, 404);

    const anonAtt = await fetch(`${h.baseUrl}${attachmentUrl}`);
    assert.equal(anonAtt.status, 401);

    const bobAtt = await fetch(`${h.baseUrl}${attachmentUrl}`, {
      headers: { Cookie: bob.cookie },
    });
    assert.equal(bobAtt.status, 404);

    const emptyChat = await fetch(`${h.baseUrl}/api/orders/${aliceOrder.orderCode}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Cookie: alice.cookie,
        'X-CSRF-Token': alice.csrfToken,
        Origin: h.baseUrl,
      },
      body: JSON.stringify({ message: '   ' }),
    });
    assert.equal(emptyChat.status, 400);

    const chatEvilSvgBoundary = '----ChatEvilSvgBoundary';
    const chatEvilSvgPayload = Buffer.concat([
      Buffer.from(
        `--${chatEvilSvgBoundary}\r\nContent-Disposition: form-data; name="image"; filename="bad.png"\r\nContent-Type: image/png\r\n\r\n`,
      ),
      Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'),
      Buffer.from(`\r\n--${chatEvilSvgBoundary}--\r\n`),
    ]);
    const chatEvilSvg = await fetch(`${h.baseUrl}/api/orders/${aliceOrder.orderCode}/messages`, {
      method: 'POST',
      headers: {
        'Content-Type': `multipart/form-data; boundary=${chatEvilSvgBoundary}`,
        Cookie: alice.cookie,
        'X-CSRF-Token': alice.csrfToken,
        Origin: h.baseUrl,
      },
      body: chatEvilSvgPayload,
    });
    assert.equal(chatEvilSvg.status, 415);

    // 6. Subida maliciosa (SVG XSS disfrazado de PNG) y Path Traversal
    const adminLoginRes = await fetch(`${h.baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Origin: h.baseUrl },
      body: JSON.stringify({ identifier: 'admin', password: 'AdminStrongPass2026!' }),
    });
    const adminData = await adminLoginRes.json();
    const adminCookie = adminLoginRes.headers.get('set-cookie').split(';')[0];

    const boundary = '----EvilBoundary';
    const evilSvg = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>');
    const evilMultipart = Buffer.concat([
      Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="productId"\r\n\r\n1\r\n` +
          `--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="hack.png"\r\nContent-Type: image/png\r\n\r\n`,
      ),
      evilSvg,
      Buffer.from(`\r\n--${boundary}--\r\n`),
    ]);
    const uploadEvil = await fetch(`${h.baseUrl}/api/admin/media`, {
      method: 'POST',
      headers: {
        'Content-Type': `multipart/form-data; boundary=${boundary}`,
        Cookie: adminCookie,
        'X-CSRF-Token': adminData.csrfToken,
        Origin: h.baseUrl,
      },
      body: evilMultipart,
    });
    assert.equal(uploadEvil.status, 415);

    const traversalRes = await fetch(`${h.baseUrl}/api/media/..%2F..%2Fpackage.json`);
    assert.equal(traversalRes.status, 404);

    // 6b. Path traversal en servidor estatico y bloqueo de exposicion de .env
    const staticDistDir = h.config.distWebDir;
    fs.mkdirSync(staticDistDir, { recursive: true });
    fs.writeFileSync(path.join(staticDistDir, 'index.html'), '<html>ok</html>');
    const envSpy = await fetch(`${h.baseUrl}/..%2F..%2F.env`);
    assert.equal(envSpy.status, 404);
    const missingWasm = await fetch(`${h.baseUrl}/evil.wasm`);
    assert.equal(missingWasm.status, 404);

    // 7. Prototype pollution rejection
    const protoRes = await fetch(`${h.baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Origin: h.baseUrl },
      body: '{"identifier":"alice_sec","password":"x","__proto__":{"isAdmin":true}}',
    });
    assert.equal(protoRes.status, 400);

    // 8. Bloqueo por fuerza bruta (5 fallos)
    for (let i = 0; i < 4; i += 1) {
      const bad = await fetch(`${h.baseUrl}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Origin: h.baseUrl },
        body: JSON.stringify({ identifier: 'bob_sec', password: 'WrongPassword1!' }),
      });
      assert.equal(bad.status, 401);
    }
    const fifth = await fetch(`${h.baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Origin: h.baseUrl },
      body: JSON.stringify({ identifier: 'bob_sec', password: 'WrongPassword1!' }),
    });
    assert.equal(fifth.status, 429);

    // 9. Concurrencia de compra sobre ultima unidad en stock (nunca stock negativo)
    const scarceProd = await fetch(`${h.baseUrl}/api/admin/products`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Cookie: adminCookie,
        'X-CSRF-Token': adminData.csrfToken,
        Origin: h.baseUrl,
      },
      body: JSON.stringify({
        categoryId: 1,
        founderTag: 'Leonel',
        name: 'Ultimo Drop',
        slug: 'ultimo-drop-race',
        subtitle: 'Solo 1 unidad',
        description: 'Prueba de atomicidad SQLite BEGIN IMMEDIATE.',
        priceCents: 9900,
        stock: 1,
      }),
    });
    const scarceId = (await scarceProd.json()).product.id;

    const charlie = await registerCustomer(h.baseUrl, 'charlie_race', 'charlie@example.com');
    for (const user of [alice, charlie]) {
      await fetch(`${h.baseUrl}/api/cart/items/${scarceId}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Cookie: user.cookie,
          'X-CSRF-Token': user.csrfToken,
          Origin: h.baseUrl,
        },
        body: JSON.stringify({ quantity: 1 }),
      });
    }

    const [r1, r2] = await Promise.all([
      fetch(`${h.baseUrl}/api/checkout`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Cookie: alice.cookie,
          'X-CSRF-Token': alice.csrfToken,
          Origin: h.baseUrl,
        },
        body: JSON.stringify({ shippingName: 'Alice' }),
      }),
      fetch(`${h.baseUrl}/api/checkout`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Cookie: charlie.cookie,
          'X-CSRF-Token': charlie.csrfToken,
          Origin: h.baseUrl,
        },
        body: JSON.stringify({ shippingName: 'Charlie' }),
      }),
    ]);

    const statuses = [r1.status, r2.status].sort((a, b) => a - b);
    assert.deepEqual(statuses, [201, 409]);

    const finalStock = h.db.prepare('SELECT stock FROM products WHERE id = ?').get(scarceId).stock;
    assert.equal(finalStock, 0);
  } finally {
    await h.cleanup();
  }
});
