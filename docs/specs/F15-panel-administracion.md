# F15 · Panel de administración y autenticación

**Estado:** Pendiente

## Historia
Como administrador, quiero iniciar sesión y gestionar Pokémon, movimientos y rutas desde un panel, sin que nadie más pueda modificar los datos (RF-11, RF-12 y RNF-05 de la entrevista).

## Comportamiento

### Backend
1. **Spring Security, sin estado.** Todos los `GET` son públicos, salvo `GET /api/pokemons/importar-rango` y `GET /api/pokemons/importar-todo`. `POST`, `PUT`, `DELETE` y la importación exigen el rol ADMIN:
   - 401 sin token, o con un token inválido o vencido
   - 403 si el token es válido pero no tiene el rol ADMIN
   - Los dos con el formato de error de siempre (`{ timestamp, status, error, mensaje, path }`)

   `GET /api/pokemons/importacion` (progreso) sigue siendo público.
2. **Login:** `POST /api/auth/login` con `{ "usuario", "contrasena" }` responde 200 con `{ token, expiraEn, usuario, rol }`.
   - El token es un JWT HS256 que dura 2 horas. Se firma y se verifica con `spring-boot-starter-oauth2-resource-server` (Nimbus).
   - Credenciales incorrectas → 401 con un mensaje genérico ("Usuario o contraseña incorrectos"), sin decir cuál falló.
   - Un campo vacío → 400.
3. **Usuarios:** tabla `usuario` (`usuario` único, `contrasena` en BCrypt y `rol`).
   - Al arrancar, si existen `ADMIN_USERNAME` y `ADMIN_PASSWORD` y ese usuario no existe, se crea con el rol ADMIN.
   - El secreto del token es `JWT_SECRETO`, de al menos 32 bytes. Si falta, el servidor no arranca y lo explica en el log.
   - Los tres valores van en `application-local.properties` o en variables de entorno, nunca en el código ni en el repo. Las contraseñas nunca se escriben en el log.
4. **CORS:** se permite `http://localhost:3000` con la cabecera `Authorization`. La lista de orígenes se puede configurar.
5. **Gestión (RF-11):**
   - Movimientos: el CRUD de F4, ahora protegido.
   - Rutas: el CRUD de F5 y, además, `DELETE /api/rutas/{id}` → 204. Borra antes sus filas de `pokemon_ruta` y de `encuentro` (F13).
   - Pokémon:
     - `PUT /api/pokemons/{id}` edita `nombreEs`, las 6 estadísticas (enteros de 1 a 255), `altura` y `peso` (≥ 0), `tipoObtencion` (uno de los 9 valores), `formaObtencion`, `tipos` (1 o 2 de los 18) y `rutas` (ids que existan). Marca `editadoAMano = true`.
     - `POST /api/pokemons` crea uno con un `idPokedex` libre mayor que 0 (si ya existe → 409) y `nombre` (slug) obligatorio, con las mismas validaciones. Responde 201 y queda `editadoAMano`.
     - `DELETE /api/pokemons/{id}` → 204. Borra sus relaciones (tipos, rutas, habilidades, aprendizaje, encuentros) y guarda el id en `pokemon_excluido`.
   - Los errores de validación son 400, y el mensaje nombra el campo: `{ ..., "mensaje": "...", "campos": { "ataque": "Debe estar entre 1 y 255" } }`. `campos` es un campo nuevo y opcional del error.
6. **Reimportación (regla de B.18 y B.28):** un Pokémon con `editadoAMano` conserva sus campos, tipos y rutas, y solo se refrescan habilidades, aprendizaje y encuentros, que el panel no edita. Los ids de `pokemon_excluido` se saltan y cuentan como `omitidos`.
7. **Postman:** `docs/postman/pokedex-admin.postman_collection.json` trae el login, que guarda el token en la variable de colección `{{token}}`, y las peticiones de crear, editar y borrar Pokémon, movimientos y rutas con `Authorization: Bearer {{token}}`. `{{baseUrl}}` es `http://localhost:8080/api`. `{{usuario}}` y `{{contrasena}}` se llenan en Postman; la colección no guarda credenciales. Sirve para la demo de la sustentación.

### Frontend (P7 `/login` y P8 `/admin`, ver `pokedex-frontend/docs/pantallas.md`)
- `/login`, con un route handler `app/api/sesion`: POST inicia sesión y guarda el token en una cookie `httpOnly; SameSite=Lax` (`Secure` en producción); DELETE cierra sesión. El token nunca va a `localStorage` ni al JavaScript del navegador.
- `/admin` con las secciones Pokémon, Movimientos y Rutas: listado con búsqueda, y formularios de crear, editar y eliminar con un diálogo de confirmación. Las escrituras pasan por el servidor de Next (route handlers o server actions), que añade `Authorization` a partir de la cookie.
- `proxy.ts` (en Next 16, `middleware` pasó a llamarse `proxy`) redirige `/admin` y sus subrutas a `/login?volver=…` si no hay cookie de sesión. Es el criterio de RNF-05.
- **Validación (RF-12):** en el cliente se revisan los campos vacíos, que los números no sean negativos y que las estadísticas sean enteros de 1 a 255. Además, cada campo muestra el 400 del servidor (`campos`).
- **Sin backend:** sin `API_URL` (sitio de Vercel con mock), `/login` y `/admin` muestran el aviso "La administración solo funciona con la API local" y no fallan; `proxy.ts` no redirige.

## Criterios de aceptación
- [ ] `POST /api/auth/login` con credenciales correctas → 200 con `token`. Con una contraseña incorrecta → 401. Con un campo vacío → 400.
- [ ] `POST /api/movimientos` sin token → 401. Con un token sin ADMIN → 403. Con ADMIN → 201.
- [ ] `GET /api/pokemons`, `GET /api/pokemons/25` y `GET /api/movimientos` sin token → 200.
- [ ] Un token vencido o firmado con otro secreto → 401.
- [ ] `GET /api/pokemons/importar-rango?total=1` sin token → 401.
- [ ] `PUT /api/pokemons/25` con `"ataque": 300` o `"ataque": "abc"` → 400, con `campos.ataque`. Uno válido → 200 y `editadoAMano: true`.
- [ ] `POST /api/pokemons` con un `idPokedex` existente → 409.
- [ ] Reimportar no pisa los cambios de un Pokémon editado ni resucita uno borrado (prueba con la PokeAPI simulada).
- [ ] `DELETE /api/rutas/{id}` → 204, y ningún Pokémon la lista después.
- [ ] El administrador inicial se crea desde `ADMIN_USERNAME`/`ADMIN_PASSWORD`, y un grep del repo no encuentra credenciales ni el secreto.
- [ ] La colección de Postman funciona de punta a punta: login → crear → editar → borrar, sin escribir el token a mano.
- [ ] Front: `/admin` sin sesión redirige a `/login`. Tras iniciar sesión, vuelve a `/admin`.
- [ ] Front: escribir "abc" en una estadística no envía el formulario y marca el error junto al campo. Un 400 del servidor también aparece junto a su campo.
- [ ] Front: en el build sin `API_URL`, `/login` y `/admin` muestran el aviso y responden sin error.

## Fuera de alcance
Registro de usuarios, recuperar la contraseña, más roles aparte de ADMIN, tokens de refresco y editar habilidades, aprendizaje o encuentros desde el panel.
