# AGENTS.md — Guía y Pautas para Agentes de IA

Este documento sirve como manual de referencia **CRÍTICO** para cualquier agente de Inteligencia Artificial o desarrollador automatizado que trabaje en el repositorio **TBD-Supply**.

---

## 🏗️ Arquitectura y Estructura del Proyecto

**TBD-Supply** es un sistema e-commerce de alto rendimiento con arquitectura dividida:

1. **`server/` — Backend Node.js (>= 24.0.0):**
   - `src/index.js`: Punto de entrada del servidor HTTP e inicialización de SQLite.
   - `src/app.js`: Configuración de middleware, rutas API y servidor estático SPA.
   - `src/routes/`: Controladores REST (`auth.routes.js`, `store.routes.js`, `cart.routes.js`, `orders.routes.js`, `admin.routes.js`).
   - `src/db/`: Conexión SQLite WAL (`connection.js`), esquema relacional (`migrations.js`), datos de prueba (`seed.js`) y activos PNG (`pngAssets.js`).
   - `src/security/`: Hashes `scrypt`, firma de sesiones `HMAC-SHA256`, tokens CSRF, cabeceras HTTP defensivas, Rate Limiter por ventana deslizante y parser multipart con validación por Magic Bytes.
   - `tests/`: Suites de pruebas automatizadas con la test runner nativa de Node.js (`integration.test.js` y `security.test.js`).

2. **`webApp/` — Frontend WebAssembly (Kotlin Compose Multiplatform + Miuix):**
   - `src/commonMain/kotlin/nose/`:
     - `model/`: DTOs serializables con `kotlinx-serialization`.
     - `network/`: Cliente HTTP reactivo y abstracción de llamadas REST.
     - `state/`: Estado global de la aplicación (`StoreAppState`).
     - `ui/components/`: Componentes adaptativos basados en [Miuix UI](https://github.com/compose-miuix-ui/miuix).
     - `ui/screens/`: Pantallas de Tienda, Detalle, Carrito, Pedidos, Chat y Panel de Administración.
     - `App.kt`: Navegación y estructura adaptable (`NavigationBar` / `NavigationRail`).

3. **`scripts/` & Instaladores:**
   - `iniciar.bat` / `bootstrap.ps1`: Automatización completa de aprovisionamiento, compilación Wasm y arranque en Windows.
   - `iniciar.sh` / `bootstrap.sh`: Instalador y lanzador automático para sistemas POSIX (Linux/macOS).

---

## ⚠️ Reglas Inviolables para Agentes de IA

1. **Cero Dependencias npm de Terceros en el Backend:**
   - El backend **DEBE** utilizar exclusivamente módulos nativos de Node.js (`node:http`, `node:sqlite`, `node:crypto`, `node:fs`, `node:path`, `node:zlib`, `node:test`, `node:assert`).
   - **NUNCA** agregues paquetes de `npm` externos (como Express, Fastify, Cors, Axios, Multer, Bcrypt, Prisma, Knex, etc.).

2. **Seguridad e Integridad Referencial:**
   - Toda consulta SQL debe ser parametrizada (`stmt.run(params)` / `stmt.all(params)` / `stmt.get(params)`).
   - Toda mutación de estado HTTP (`POST`, `PUT`, `PATCH`, `DELETE`) requiere validación de origen y la cabecera `X-CSRF-Token` cuando el usuario está autenticado.
   - Las transacciones de checkout de stock **DEBEN** ejecutarse usando `BEGIN IMMEDIATE` en SQLite para prevenir condiciones de carrera.

3. **Manejo Estricto de Datos Sensibles y Archivos:**
   - **NUNCA** incluyas en commits archivos `.env`, bases de datos SQLite (`*.sqlite*`), imágenes subidas por usuarios (`server/storage/uploads/*`) ni logs temporales (`*.log`).
   - Los secretos e identificadores de sesión deben tratarse con hashes en tiempo constante (`crypto.timingSafeEqual`).

4. **Verificación de Pruebas:**
   - Antes de dar por finalizada cualquier tarea de edición de código en el backend, ejecuta las pruebas de integración y seguridad:
     ```bash
     node --test server/tests/integration.test.js server/tests/security.test.js
     ```
   - Asegúrate de que **todas las pruebas pasen sin fallos**.

5. **Formato de Commits:**
   - Usa la convención **Conventional Commits 1.0.0** (`feat:`, `fix:`, `docs:`, `style:`, `refactor:`, `perf:`, `test:`, `build:`, `ci:`).
