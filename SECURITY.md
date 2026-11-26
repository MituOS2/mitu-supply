# Política de Seguridad — TBD.SUPPLY

La seguridad es un pilar central del diseño de **TBD.SUPPLY**. El backend está construido desde cero sobre módulos nativos de Node.js para minimizar la superficie de ataque y eliminar riesgos de cadena de suministro.

---

## Versiones Soportadas

| Versión | Soportada |
| :--- | :---: |
| `1.x` (`main`) | ✅ Sí |

---

## Reporte de Vulnerabilidades

Si descubres una vulnerabilidad de seguridad en este repositorio:

1. **No abras un Issue público** con los detalles de explotación.
2. Utiliza la pestaña **Security → Advisories → Report a vulnerability** del repositorio en GitHub (si está habilitada) o contacta directamente al mantenedor del repositorio (`@xUsoCarrusoX3`) por un canal privado.
3. Incluye en tu reporte:
   - Descripción clara del problema y el componente afectado (`server/`, `webApp/`, scripts).
   - Pasos mínimos para reproducirlo o prueba de concepto (PoC).
   - Impacto estimado y, si es posible, mitigación sugerida.

Una vez validado el reporte, se coordinará una corrección, una prueba de regresión en `server/tests/security.test.js` y la publicación del parche.

---

## Controles de Seguridad Implementados

- **Autenticación y contraseñas:** Derivación con `crypto.scrypt` (`N=16384`, `r=8`, `p=1`, clave de 64 bytes, salt aleatorio de 16 bytes), comparación en tiempo constante (`crypto.timingSafeEqual`) y verificación dummy frente a enumeración por timing.
- **Sesiones opacas:** Cookie `tbd_sid` de 256 bits (`HttpOnly; SameSite=Strict`); en SQLite solo se almacena el `HMAC-SHA256` con `SESSION_PEPPER`. Expiración absoluta de 7 días e inactividad máxima de 12 horas.
- **Protección CSRF:** Validación de `Sec-Fetch-Site` / `Origin` / `Referer` junto con token `X-CSRF-Token` ligado a sesión y verificado en tiempo constante.
- **Autorización y Anti-IDOR:** Control RBAC estricto (`customer` / `admin`) verificado contra base de datos; carritos, pedidos, mensajes y adjuntos están aislados por propietario.
- **Subida de imágenes:** Validación binaria por **Magic Bytes** (`PNG`, `JPEG`, `WebP`), bloqueo explícito de `SVG` contra Stored XSS, límite de 3 MB, renombre a `UUIDv4` y entrega con `Content-Security-Policy: default-src 'none'; sandbox` + `X-Content-Type-Options: nosniff`.
- **Integridad de inventario:** Checkout ejecutado dentro de transacciones SQLite `BEGIN IMMEDIATE` con descuento atómico condicional (`stock >= ?`).
- **Defensa ante abuso:** Rate limiter por ventana deslizante, bloqueo temporal de 10 minutos tras 5 intentos fallidos de login, timeouts HTTP explícitos y registro en `audit_logs`.

---

## Endurecimiento para Producción

Antes de exponer una instancia en un entorno productivo, asegúrate de configurar en `.env`:

- `NODE_ENV=production`
- `LOCAL_AUTO_ADMIN=false` *(desactiva el inicio de sesión administrativo automático en `localhost`)*
- `COOKIE_SECURE=true` *(detrás de HTTPS o reverse proxy TLS)*
- `TRUST_PROXY=true` *(solo si existe un reverse proxy confiable como Nginx, Caddy o Cloudflare)*
- `ALLOWED_ORIGINS=https://tudominio.com`
- `SESSION_PEPPER` e `IP_HASH_SALT` con al menos 32 bytes hexadecimales criptográficamente aleatorios
- `ADMIN_BOOTSTRAP_PASSWORD` robusta (o rotarla inmediatamente tras el primer arranque)
