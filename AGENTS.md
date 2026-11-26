# AGENTS.md — Guía para Agentes de IA

Este repositorio contiene **TBD-Supply**, una aplicación web de tienda en línea con frontend nativo en Kotlin/Compose Multiplatform (Miuix WebAssembly) y backend en Node.js puro sin dependencias de terceros.

## Contexto y Estructura del Proyecto

- `server/`: Backend en Node.js (>= 24).
  - `src/index.js`: Punto de entrada del servidor HTTP.
  - `src/app.js`: Configuración de la aplicación y enrutador.
  - `src/routes/`: Rutas API (`auth`, `store`, `cart`, `orders`, `admin`).
  - `src/db/`: Conexión a SQLite, esquema y migraciones/semillas.
  - `src/security/`: Lógica de contraseñas, sesiones, CSRF, validación y sanitización.
- `webApp/`: UI nativa en Kotlin Compose Multiplatform (Miuix) compilada a WebAssembly (`wasmJs`).
  - `src/commonMain/kotlin/`: Interfaz gráfica con componentes Miuix.
- `iniciar.bat` / `iniciar.sh`: Scripts de arranque automático en 1 clic.

## Tecnologías Utilizadas

- **Frontend:** Kotlin Multiplatform + Compose Wasm + Miuix UI (`compose-miuix-ui`).
- **Backend:** Node.js (módulos nativos `node:http`, `node:sqlite`, `node:crypto`, `node:zlib`).
- **Base de Datos:** SQLite nativo integrado (`node:sqlite`).

## Pautas y Reglas para Agentes

1. **Cero Dependencias de Terceros en Backend:** Mantener la arquitectura en Node.js usando sólo librerías nativas.
2. **Exclusión de Datos Sensibles y BD:** No incluir `.env` ni bases de datos SQLite (`*.sqlite`) en las confirmaciones de Git.
3. **Pruebas:** Validar los cambios ejecutando `npm test`.
