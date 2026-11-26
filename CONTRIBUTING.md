# Guía de Contribución — TBD.SUPPLY

¡Gracias por tu interés en contribuir a **TBD.SUPPLY**! Esta guía describe las convenciones, el flujo de trabajo y los principios arquitectónicos del proyecto para mantener el repositorio limpio, seguro y mantenible.

---

## Principios del Proyecto

Antes de abrir un Pull Request, ten en cuenta las reglas fundamentales del repositorio:

1. **Cero dependencias npm de terceros en el backend:** `server/` debe utilizar exclusivamente módulos nativos de Node.js (`node:http`, `node:sqlite`, `node:crypto`, `node:fs`, `node:path`, `node:zlib`, `node:test`, etc.).
2. **Seguridad por diseño:** Todo nuevo endpoint que modifique estado debe exigir validación de origen/CSRF (`requireCsrf` / `requireSameOrigin`), validación estricta de entrada, consultas SQL parametrizadas y control de acceso explícito (`requireAuth` / `requireAdmin`).
3. **Nunca subir secretos ni datos de ejecución:** No incluyas `.env`, bases de datos SQLite (`*.sqlite*`), archivos subidos (`server/storage/`) ni compilados (`server/dist-web/`, `vendor/`).
4. **Precios en centavos enteros (USD):** Toda aritmética monetaria debe operar sobre enteros (`price_cents`, `subtotal_cents`, `total_cents`) para evitar errores de punto flotante.

---

## Entorno de Desarrollo

1. Asegúrate de tener instalados **Node.js >= 24**, **JDK 21+** y **Git**.
2. Clona el repositorio y crea una rama descriptiva desde `main`:
   ```bash
   git clone https://github.com/xUsoCarrusoX3/TBD-Supply.git
   cd TBD-Supply
   git checkout -b feat/nombre-descriptivo
   ```
3. Para levantar el entorno completo (descarga de `vendor/miuix`, generación de `.env` y compilación Wasm):
   - **Windows:** `iniciar.bat`
   - **Linux / macOS:** `./iniciar.sh`
4. Si solo trabajas en el backend:
   ```bash
   npm run dev
   ```

---

## Convención de Commits (Conventional Commits)

Este repositorio adopta la especificación [Conventional Commits 1.0.0](https://www.conventionalcommits.org/es/v1.0.0/):

```text
<tipo>(<alcance opcional>): <descripción breve en imperativo>

[cuerpo opcional explicando el porqué y los detalles relevantes]

[pie opcional: Closes #123, BREAKING CHANGE: ...]
```

### Tipos permitidos

| Tipo | Uso |
| :--- | :--- |
| `feat` | Nueva funcionalidad en backend, frontend o scripts |
| `fix` | Corrección de un error o fallo de seguridad |
| `docs` | Cambios exclusivos en documentación (`README.md`, `SECURITY.md`, etc.) |
| `style` | Formato, espacios o estilo sin alterar lógica |
| `refactor` | Reestructuración de código sin cambiar comportamiento externo |
| `perf` | Mejoras de rendimiento |
| `test` | Adición o actualización de pruebas automatizadas |
| `build` | Cambios en Gradle, compilación Wasm o empaquetado |
| `ci` | Workflows de GitHub Actions y automatización |
| `chore` | Mantenimiento general, licencias, configuración (`.editorconfig`, etc.) |

### Alcances sugeridos

`api`, `auth`, `cart`, `orders`, `admin`, `db`, `security`, `ui`, `wasm`, `scripts`, `readme`, `github`, `config`, `license`.

### Ejemplos

```text
feat(orders): add image attachment support to order chat
fix(security): enforce strict content-type validation on multipart uploads
docs(readme): document production environment variables
test(security): cover concurrent checkout stock race condition
```

---

## Validación antes de abrir un Pull Request

1. Ejecuta la suite completa de integración y seguridad:
   ```bash
   npm test
   ```
2. Si modificaste archivos en `webApp/`, verifica que el bundle WebAssembly compile sin errores:
   - **Windows:** `iniciar.bat -BuildOnly -Rebuild`
   - **Linux / macOS:** `./iniciar.sh --build-only --rebuild`
3. Revisa que tu diff no contenga archivos ignorados ni cambios fuera de alcance (`git status` y `git diff`).
4. Abre el Pull Request completando la plantilla (`.github/PULL_REQUEST_TEMPLATE.md`) y verifica que el workflow de CI en GitHub Actions pase en verde.
