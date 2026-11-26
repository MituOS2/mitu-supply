# Changelog

Todos los cambios relevantes de **TBD.SUPPLY** se documentan en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y este proyecto sigue [Versionado Semántico (SemVer)](https://semver.org/lang/es/).

---

## [Unreleased]

### Added
- Licencia **MIT** (`LICENSE`) y metadatos completos de repositorio en `package.json` y `server/package.json`.
- Configuración de formato consistente multiplataforma mediante `.editorconfig` y reglas ampliadas en `.gitattributes`.
- Integración continua con GitHub Actions (`.github/workflows/ci.yml`) ejecutando las suites de integración y seguridad en Node.js 24.
- Plantillas de GitHub para Pull Requests (`.github/PULL_REQUEST_TEMPLATE.md`) y formularios de Issues (`.github/ISSUE_TEMPLATE/`).
- Documentación de gobernanza: guía de contribución (`CONTRIBUTING.md`), política de seguridad (`SECURITY.md`) y registro de cambios (`CHANGELOG.md`).
- Documentación técnica ampliada en `README.md` (diagrama de arquitectura, referencia completa de API REST, variables de entorno, esquema SQLite y guía de desarrollo).

### Changed
- Ampliado `.env.example` con variables opcionales de seguridad y entorno comentadas (`DIST_WEB_DIR`, `ALLOWED_ORIGINS`, `TRUST_PROXY`, `COOKIE_SECURE`, `LOCAL_AUTO_ADMIN`, `SEED_DEMO_CATALOG`).

---

## [1.0.0] - 2026-11-19

### Added
- Frontend nativo en **Kotlin Multiplatform + Compose Multiplatform (`wasmJs`)** con componentes **Miuix** (`compose-miuix-ui`), temas dinámicos (Claro, Oscuro, Sistema, Monet y Squircle) y navegación adaptable móvil/escritorio.
- Backend HTTP en **Node.js (>= 24)** sin dependencias npm de terceros (`node:http`, `node:sqlite`, `node:crypto`, `node:zlib`).
- Base de datos **SQLite** integrada en modo `WAL` con 10 tablas, migraciones idempotentes, semilla opcional de catálogo y checkout atómico (`BEGIN IMMEDIATE`).
- API REST completa para tienda pública, autenticación, carrito con sincronización (`merge`), checkout en USD, seguimiento y chat por pedido con adjuntos de imagen, y panel de administración con auditoría.
- Capa de seguridad construida desde cero (`scrypt`, sesiones HMAC-SHA256 en cookie `HttpOnly; SameSite=Strict`, doble protección CSRF, RBAC/Anti-IDOR, validación de imágenes por Magic Bytes sin SVG, CSP estricta y rate limiting).
- Lanzadores automáticos multiplataforma en 1 clic (`iniciar.bat`, `iniciar-publico.bat` con Cloudflare Quick Tunnel e `iniciar.sh`).
- Suites automatizadas de pruebas de integración y auditoría de seguridad (`npm test`).
