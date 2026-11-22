# Resumen de la tarea 4.2 (corrección de la interventoría)

**Fecha:** 2026-11-22 · **Hecho sin aprobación intermedia** (el usuario estaba ausente y pidió seguir solo). **Sin `git push`**: todo está en local.
Repos: `pokedex-backend` (rama `main`) y `pokedex-frontend` (rama `master`). Hallazgos y su marca «Corregido»: `docs/calidad/interventoria.md`.

## 1. Commits (en orden de ejecución)

| # | Repo | Commit | Hallazgos |
|---|---|---|---|
| G1 | backend | `bd5d06b` fix: seguridad del backend (S-01, S-02, S-03, S-04, S-08, S-09) | S-01, S-02, S-03, S-04 (cierra también C-03), S-08, S-09 |
| G2 | frontend | `b8f05bd` fix: robustez del front en login y edición de movimientos (S-06, S-07) | S-06, S-07 |
| G4 | frontend | `254c8b8` fix: accesibilidad táctil y aria-label (A-01, A-02) | A-01, A-02 (cierra también Q-11) |
| G3 | frontend | `1a9f1ff` docs: comentario de http.ts con la medición vigente (D-09) | D-09 |
| G3 | backend | «docs: specs, README y conteos de la interventoría (D-01, T-06, T-16, T-17, D-04, D-12)» (el commit que incluye este archivo; ver `git log -1` en el backend) | D-01, T-06 (F5), T-16, T-17, D-04, D-12 (solo conteos) |

**Desvío del orden pedido:** hice el grupo 4 *antes* que el 3, para que los conteos de pruebas del grupo 3 ya incluyan las pruebas nuevas del grupo 4. El grupo 3 son dos commits porque los docs compartidos viven en el backend y `http.ts` en el frontend. Como acordamos, las marcas en `interventoria.md` dicen «grupo N» y el mensaje del commit, **sin hash** (el hash de un commit no puede ir dentro de él); los hashes están en esta tabla.

## 2. Pruebas antes y después

| Suite | Antes | Después | Cómo se obtuvo |
|---|---|---|---|
| Backend (`.\mvnw.cmd -B test`) | **204**, 0 fallos | **244**, 0 fallos (+40) | Corrido por mí por PowerShell: «antes» en un `git worktree` del commit `6dd6154`; «después» en el árbol de trabajo con el grupo 1 (el grupo 3 backend es solo docs) |
| Frontend Vitest (`npm run test`) | 146 + 29 de contrato omitidas | **157** + 29 omitidas (+11) | Corrido por mí |
| Frontend E2E (Playwright) | 11 (según la interventoría) | **12** en 4 archivos | Solo `npx playwright test --list`; **no se ejecutaron** (necesitan el backend con perfil `e2e` y la BD con datos) |
| `contrato.test.ts` (29) | omitidas | omitidas | Necesita `API_URL` con el backend arrancado: no se corrió |
| `npm run lint` | 0 errores, 10 avisos | 0 errores, 10 avisos | Los avisos ya existían |
| `npm run build` | — | OK, también con `API_URL=http://127.0.0.1:9/api` | Corrido tras los grupos 2 y 4 |

Pruebas nuevas del backend (+40): `LimiteLoginTest` 7, `LoginConLimiteTest` 9, `ManejoErroresPeticionTest` 5, `MovimientoValidacionTest` 6, `RutaValidacionTest` 6, `SecretosPorDefectoTest` 6, y 1 en `ApiConPostgresTest` (`sortInvalidoNoRevelaNombresInternos`).
Pruebas nuevas del frontend (+11): `app/api/sesion/route.test.ts` 4, `lib/api/admin.test.ts` 4, `lib/accesibilidad.test.ts` 3.

**«Rojo» antes del arreglo:** en el frontend lo comprobé corriendo Vitest antes de tocar el código (S-06: 2 pruebas en rojo; S-07: 2; A-01/A-02: las 3). En el backend las pruebas nuevas usan clases que aún no existían, así que no compilaban (rojo por compilación); no corrí una suite roja intermedia.

**Discrepancia de conteos:** `CLAUDE.md` y la bitácora decían **210** pruebas de backend y la interventoría contó 205 `@Test`; Maven dio **204** sobre el commit anterior a mis cambios. No pude reproducir el 210; lo dejé anotado en `BITACORA_IA.md`. Ahora `CLAUDE.md` dice 244.

## 3. Lo que debes revisar antes del push

1. **Un único cambio en una prueba existente** (`SeguridadControllerTest`): añadí `LimiteLogin.class` a su `@Import` (y su `import`). `AuthController` ahora necesita ese bean y un `@WebMvcTest` no escanea `@Component`; no cambié ninguna aserción. Además, `ApiConPostgresTest` recibió un método nuevo (no se editó el existente `sortPorUnCampoInexistenteResponde400`).
2. **S-08 (sin contraseña de BD por defecto):** el servidor ya no arranca sin `DB_PASSWORD` o `SPRING_DATASOURCE_PASSWORD`. No leí tu `application-local.properties` (regla del proyecto): arranca el servidor una vez en local para confirmar que ahí está `DB_PASSWORD`. Render y el Dockerfile ya definen `SPRING_DATASOURCE_PASSWORD` (según `despliegue.md`).
3. **S-09 + `Dockerfile`:** el secreto de relleno del entrenamiento CDS contenía una de las frases que ahora se rechazan; lo cambié por otro valor fijo. **No pude construir la imagen** (no probé Docker): el CI la construye en el PR; confirma que el build sigue generando `app.jsa` (el build falla si no se genera).
4. **S-01, política elegida (mía, no del texto de la tarea):** 5 fallos por minuto por IP, solo cuentan los fallos, el 6.º intento responde 429 aunque la contraseña sea correcta, y se corta antes de BCrypt. Es en memoria y por instancia. La IP sale de `request.getRemoteAddr()` (como F20, con `forward-headers-strategy=native`): confirma en Render que ve la IP real; si no, todos los usuarios compartirían cupo. Topes de largo: usuario ≤ 100, contraseña ≤ 128 (BCrypt solo usa 72 bytes; no cambió nada visible).
5. **S-03, desviación del texto del hallazgo:** el 400 de `sort` dice «El parámetro 'sort' no es válido: el campo 'foo' no existe» (repite solo lo que el cliente envió). No añadí «la lista de campos permitidos» porque el contrato no define una lista por endpoint; además, la prueba existente exige que el mensaje contenga `'foo'`.
6. **S-02:** `DatosMovimiento` y `DatosRuta`; se conserva «primer error gana» (no se acumulan campos) para no cambiar los mensajes que ya esperan las pruebas. `descripcion` de ruta: tope nuevo de 2000 caracteres (decisión mía; está en `api.md` y `modelo.md`). Rutas existentes con descripciones más largas seguirían legibles, pero un `PUT` con ese texto ahora daría 400.
7. **F5/estados de specs:** F5, F7 y F14 ya no dicen «criterios con pruebas automáticas» sino «parciales» (los huecos son del grupo 5). F15 y F20 pasaron a «Implementada». F11–F13 siguen «Pendiente» (coherente con `tareas.md`). El criterio de F5 de `DELETE` quedó en 204/404/401; la fila de `decisiones.md` es del 2026-11-22.
8. **D-12 solo parcial:** corregí los conteos; el CI del frontend sigue sin ejecutar E2E ni `contrato.test.ts`. **B.39 sigue abierto** (solo le añadí los totales medidos).
9. **Efectos laterales en tu máquina:** corrí `npm run build` mientras tu servidor de desarrollo seguía en el puerto 3000 y lo usé en solo lectura para medir a 360 px (chip «×» 44×44 px, «Quitar filtros» 44 px, 0 `<span aria-label>`). Si el dev server se comporta raro, reinícialo. Quedaron cambios de línea LF→CRLF avisados por git (no relevantes).
10. Revisa los diffs: `git show bd5d06b` (backend) y `git show b8f05bd 254c8b8 1a9f1ff` (frontend).

## 4. Pendientes (no se tocaron)

- **Fuera del alcance pedido:** grupo 5 (pruebas faltantes T-01 a T-15, salvo lo ya cubierto) y **S-17** (`vitest` con vulnerabilidades de desarrollo).
- **Sin corregir de la interventoría:** S-05, S-10 a S-16, S-18 a S-21; C-01, C-02, C-04 a C-08 (C-03 sí, con S-04); Q-01 a Q-10, Q-12, Q-13; A-03 a A-05; D-02, D-03, D-05 a D-08, D-10, D-11, D-13; T-01 a T-05, T-07 a T-15. Siguen en el orden sugerido de la sección 10 de la interventoría (7.º grupo: documentación).
- **Pendientes justificados** (sin cambios): `ddl-auto=update` frente a Flyway, revocación de tokens, `open-in-view`, límite de uso en memoria por instancia.
- **Datos pendientes de comprobar a mano:** arranque real tras S-08/S-09, imagen Docker, E2E y contrato con el backend arrancado.
- Plan: B.49 (corrección de hallazgos) queda **abierta** en `tareas.md`: se cerró un subconjunto; no la marqué como hecha.
