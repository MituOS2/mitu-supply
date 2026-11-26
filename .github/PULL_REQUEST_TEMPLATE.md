## Descripción

<!-- Resume qué resuelve o incorpora este Pull Request y el contexto necesario para revisarlo. -->

## Tipo de cambio

- [ ] `feat`: Nueva funcionalidad
- [ ] `fix`: Corrección de error
- [ ] `security`: Mejora o corrección de seguridad
- [ ] `refactor`: Refactorización sin cambio funcional
- [ ] `docs`: Documentación
- [ ] `test`: Pruebas automatizadas
- [ ] `chore` / `ci`: Mantenimiento, configuración o CI/CD

## Pruebas realizadas

- [ ] `npm test` (integración + auditoría de seguridad) ejecutado y en verde
- [ ] Verificación manual en navegador / compilación Wasm (si aplica a `webApp/`)

## Checklist

- [ ] El backend mantiene **cero dependencias npm de terceros** (solo módulos nativos `node:*`).
- [ ] No se han incluido archivos sensibles (`.env`, `*.sqlite`, imágenes en `server/storage/`).
- [ ] Los commits siguen el estándar **Conventional Commits** (`feat:`, `fix:`, `docs:`, etc.).
- [ ] La documentación (`README.md`, `.env.example`, `CHANGELOG.md`) fue actualizada si corresponde.
