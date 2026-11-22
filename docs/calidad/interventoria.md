# Interventoría de pokedex-backend y pokedex-frontend

**Tarea:** 4.1 del plan (B.48) · **Fecha:** 2026-11-21 · **Tipo:** auditoría de solo lectura, sin cambios de código.
**Contra qué se auditó:** `docs/contratos/api.md`, `docs/specs/F1`–`F20`, `docs/decisiones.md`, `docs/pruebas.md`, los `CLAUDE.md` de los dos repos y la skill `.claude/skills/backend-seguro`.

## 1. Resumen

| Severidad | Cantidad |
|---|---|
| Crítico | 0 |
| Alto | 2 |
| Medio | 24 |
| Bajo | 51 |
| **Total** | **77** |

- **No hay hallazgos críticos.** No se encontraron secretos reales en el código ni en el historial de git de ninguno de los dos repos, y no hay endpoints de escritura sin protección.
- **Alto:**
  - S-01: el login no tiene límite de intentos.
  - D-01: los comandos de importación del README del backend devuelven 401 tal como están escritos.
- **Lo más urgente de corregir:** S-01, S-02 (validación de `Movimiento` y `Ruta`), S-03 (mensaje de `sort`), S-04 (415/406 como 500) y D-01.
- **Pruebas:** hay criterios de aceptación sin prueba automática en F1–F7, F9, F14, F15, F19 y F20, además de F11–F13 completas (pendientes por diseño). Ver la sección 4.
- **Documentación:** varios estados de spec y conteos de pruebas contradicen el código. Ver la sección 6.

**Positivo (se conserva):**
- `JWT_SECRETO` sin valor por defecto y con mínimo de 32 bytes.
- Algoritmo HS256 fijado en el decoder.
- Login con tiempo constante.
- El 500 es genérico y el detalle va solo al log.
- Validación de foto por magic bytes.
- Límite de 10 identificaciones por minuto por IP.
- Sin `actuator`.
- Docker sin root.
- Cookie de sesión `httpOnly` + `sameSite=lax`.
- `destinoSeguro` contra open redirect.
- El frontend no usa ninguna variable `NEXT_PUBLIC_*`.
- Historial de git sin claves.
- Sin TODO/FIXME en ningún repo.

## 2. Alcance y método

- **Hecho:**
  - Lectura del código y de los docs de los dos repos.
  - Búsqueda en el historial de git (`git log -S` sobre `password`, `secret`, `JWT_SECRETO`, `GEMINI_API_KEY`, `api key`, `AIza`, `NEXT_PUBLIC`) y de archivos añadidos como `.env*` o `application-local.properties`.
  - `npm audit` en el frontend.
  - Listado de dependencias de runtime en Maven.
  - Conteo de `@Test` y de `it(...)`.
  - Cruce de cada criterio de cada spec con las pruebas.
- **No hecho:**
  - No se ejecutaron `.\mvnw.cmd test`, `npm run test` ni los E2E (`CLAUDE.md` pide lanzar Maven desde la terminal de la app).
  - No se probó contra producción ni con `curl.exe`.
  - No se abrieron las pantallas en un navegador: el repaso de accesibilidad y responsive es de código.
  - No se leyó `application-local.properties` ni ningún `.env*` con valores; solo se confirmó su existencia y que están fuera de git.
- **Límite del análisis de dependencias de Maven:** el proyecto no tiene plugin de auditoría (OWASP `dependency-check`, Dependabot) y no hay base de vulnerabilidades local. Solo se listaron las versiones (sección 3.1, S-17); **no se verificó si tienen CVE**. Está propuesto como corrección.
- La severidad es la del riesgo en este proyecto (API pública pequeña, un solo administrador, despliegue gratuito), no la de una auditoría genérica.

## 3. Seguridad

### 3.1 Hallazgos

| ID | Hallazgo | Archivo:línea | Sev. | Cómo comprobarlo | Corrección propuesta (sin aplicar) |
|---|---|---|---|---|---|
| S-01 | `POST /api/auth/login` no tiene límite de intentos, bloqueo ni retraso. Solo lo frena el coste de BCrypt. Tampoco hay tope de largo del usuario ni de la contraseña. | `pokedex-backend/.../controller/AuthController.java:44-45` | **Alto** | Enviar 100 logins fallidos seguidos desde una IP: todos responden 401, ninguno 429. | Reutilizar el patrón de `LimiteIdentificaciones` (ventana por IP, p. ej. 5 fallos por minuto → 429) y topar los largos (usuario ≤ 100, contraseña ≤ 128). Añadir la prueba de regresión y el caso en `api.md`. <br>**Corregido** — grupo 1, commit «fix: seguridad del backend (S-01, S-02, S-03, S-04, S-08, S-09)». |
| S-02 | `POST/PUT` de movimientos y rutas reciben la **entidad JPA** por `@RequestBody`. Se limpian `id` e `idPokeapi`, pero no hay validación de largo (`nombre` 100, `categoria` 50…): un valor largo llega a la BD y responde 500 en lugar de 400. `descripcion` de `Ruta` es TEXT sin tope. Contradice la skill `backend-seguro` §1 (usar `record` o `Map`, como hace `DatosPokemon`). | `MovimientoController.java:71,83` · `RutaController.java:61,75` | Medio | `POST /api/movimientos` con `nombre` de 500 caracteres y token ADMIN: se espera 400, responde 500. | Pasar a `record` de entrada (o `Map` + validador como `DatosPokemon`) con los largos del esquema, y lanzar `CamposInvalidosException`. Prueba de regresión por campo. <br>**Corregido** — grupo 1, commit «fix: seguridad del backend (S-01, S-02, S-03, S-04, S-08, S-09)». |
| S-03 | El 400 de `sort` inválido refleja `PropertyReferenceException.getMessage()`, que puede incluir el nombre de la clase y del campo (`No property 'x' found for type 'Pokemon'`). Es una fuga leve de nombres internos. | `exception/GlobalExceptionHandler.java:157-159` | Medio | `GET /api/pokemons?sort=foo` y leer el `mensaje`. | Mensaje fijo: «El parámetro 'sort' no es válido» con la lista de campos permitidos del contrato. Actualizar la prueba de `API:148`. <br>**Corregido** — grupo 1, commit «fix: seguridad del backend (S-01, S-02, S-03, S-04, S-08, S-09)». |
| S-04 | No hay handler para `HttpMediaTypeNotSupportedException` (415) ni `HttpMediaTypeNotAcceptableException` (406). Caen en el 500 genérico. El contrato tampoco los documenta. | `GlobalExceptionHandler.java` (ausente) | Medio | `POST /api/auth/login` con `Content-Type: text/plain`: se espera 415, responde 500. | Dos `@ExceptionHandler` con el mismo JSON de error, y filas 415/406 en la tabla de errores del contrato. Regla de la skill §4 («excepciones nuevas de Spring se manejan ahí»). <br>**Corregido** — grupo 1, commit «fix: seguridad del backend (S-01, S-02, S-03, S-04, S-08, S-09)». |
| S-05 | `IdentificacionService.leer()` lanza `UncheckedIOException` sin handler propio: caería en el 500 genérico si falla la lectura del archivo subido. | `service/IdentificacionService.java:99` | Bajo | Difícil de provocar; se comprueba leyendo el código. | Traducir a 400 («No se pudo leer la foto») o a 502, según la causa. |
| S-06 | El login del frontend: el `await respuesta.json()` queda fuera de todo `try`. Si el backend responde 200 con un cuerpo que no es JSON, la ruta lanza un 500 sin mensaje en lugar del 502 con texto. | `pokedex-frontend/app/api/sesion/route.ts:47` | Medio | Apuntar `API_URL` a un servidor que devuelva HTML con 200 y enviar el formulario de `/login`. | Envolver en `try/catch` y devolver `error(502, "No se pudo iniciar sesión…")`, igual que los otros dos fallos de la misma ruta. <br>**Corregido** — grupo 2, commit «fix: robustez del front en login y edición de movimientos (S-06, S-07)». |
| S-07 | `obtenerMovimiento` hace `fetch` sin `AbortSignal.timeout` y lanza un `Error` genérico, a diferencia del resto de `lib/api`, que usa `TIEMPO_ESPERA_MS` y `ErrorApi`. | `pokedex-frontend/lib/api/admin.ts:54-58` | Medio | Con el backend dormido, el formulario de edición de un movimiento no termina nunca de cargar. | Usar el helper común de `http.ts` (timeout + `ErrorApi`). <br>**Corregido** — grupo 2, commit «fix: robustez del front en login y edición de movimientos (S-06, S-07)». |
| S-08 | `application.properties` deja el usuario y la contraseña de BD con valor por defecto `postgres`. La skill §5 pide «sin valor por defecto para un secreto». Solo aplica si no hay variables. | `pokedex-backend/src/main/resources/application.properties:28-29` | Medio | Arrancar sin `DB_PASSWORD` contra una BD con `postgres/postgres`: conecta. | Quitar el valor por defecto de la contraseña y fallar al arrancar si falta fuera del perfil local, o moverlo a `application-local.properties`. <br>**Corregido** — grupo 1, commit «fix: seguridad del backend (S-01, S-02, S-03, S-04, S-08, S-09)». |
| S-09 | El secreto JWT de ejemplo (`.example`) es público y el servidor solo valida el **largo** mínimo (32 bytes), no la entropía. Quien copie el ejemplo tal cual arranca con un secreto conocido. | `config/SeguridadConfig.java:46,93-99` · `application-local.properties.example` | Medio | Copiar el `.example` sin cambiar `JWT_SECRETO` y arrancar: lo acepta. | Rechazar al arrancar los valores que contengan `cambia_esto` o `entrenamiento-sin-valor-real`, o generar uno aleatorio efímero en el perfil local. <br>**Corregido** — grupo 1, commit «fix: seguridad del backend (S-01, S-02, S-03, S-04, S-08, S-09)». |
| S-10 | `.gitignore` del backend no cubre `.env*`. La skill §5 los nombra como archivos que no deben subirse. Hoy no hay ninguno en el repo ni en el historial. | `pokedex-backend/.gitignore` | Bajo | `git check-ignore .env` no lo ignora. | Añadir `.env*` (con excepción de `.env.example`, si existiera). |
| S-11 | El frontend tiene un `.env.test` local con la contraseña de desarrollo del admin. Está ignorado por git y no aparece en el historial. | `pokedex-frontend/.env.test` | Bajo | `git check-ignore .env.test` lo ignora; `git log --all -- .env.test` sin resultados. | Ninguna acción necesaria; se deja constancia. |
| S-12 | Token JWT sin `jti`, sin revocación ni refresh, y el decoder valida firma y `exp` pero no el emisor (`iss`). Dura 120 min. | `service/ServicioToken.java:37-44` · `SeguridadConfig.java:110` | Bajo | Un token robado sirve hasta que vence. | Validar `iss` en el decoder. La revocación se deja como pendiente justificado (un solo administrador). |
| S-13 | No hay límite de uso fuera de identificar: ni login (S-01) ni `GET /api/pokemons` (`size` hasta 200 con entidades completas). | `application.properties` (`max-page-size=200`) | Bajo | Pedir `size=200` en bucle. | Límite global por IP en el proxy (Render/Vercel) o reducir `max-page-size`. |
| S-14 | `spring.jpa.open-in-view=true` mantiene una conexión por petición. Con el pool de 5 y llamadas a Gemini de hasta 20 s, unas pocas fotos lentas agotan el pool. Está documentado como decisión por las relaciones lazy. | `application.properties:57` · `service/ServicioVisionGemini` | Bajo | 6 identificaciones simultáneas lentas bloquean las lecturas. | Cargar las relaciones necesarias con `@EntityGraph`/DTOs y apagar `open-in-view`; o dejarlo como pendiente justificado. |
| S-15 | `GET /api/pokemons/importacion` (estado de la importación) es público por la regla `GET /**`. El contrato lo marca como «uso interno». | `SeguridadConfig.java:65` · `api.md` | Bajo | Llamarlo sin token: 200. | Pasarlo a ADMIN, o documentar que es público a propósito. |
| S-16 | `GET /pokemons/importar-rango` y `importar-todo` hacen escrituras por GET. Están protegidas por ADMIN, pero un GET con efectos es poco ortodoxo. | `controller/PokemonController.java:210` | Bajo | Ver el contrato (`api.md:34`). | Dejar solo POST cuando se pueda; el README ya pide token (ver D-01). |
| S-17 | **Dependencias:** `npm audit` en el frontend reporta 3 vulnerabilidades, **todas de desarrollo** (no viajan en el build): `vitest` 0.0.95–4.1.10 (el repo usa `^3.2.7`), `tinypool` ≤ 2.1.1 (npm lo marca **crítico**: gadget de contaminación de prototipos a ejecución remota en las opciones de los workers) y `@vitest/mocker` (moderado: lectura de archivos / inyección vía mocks). Solo se explotan con entrada controlada por un atacante en el ejecutor de pruebas, que aquí solo corre código propio en local y en CI. | `pokedex-frontend/package.json:29` (`vitest`) | Medio | `cd pokedex-frontend && npm audit` → 3 vulnerabilidades (1 moderada, 2 críticas). | Subir `vitest` a una versión corregida (npm propone 5.1.1, cambio mayor: probar). Revisar que `vitest.config.ts` y los mocks sigan funcionando antes de fusionar. Añadir `npm audit --omit=dev` al CI. |
| S-18 | **Dependencias del backend:** no hay plugin ni paso de CI que analice vulnerabilidades. Versiones de runtime (gestionadas por Spring Boot 4.1.2): spring-webmvc/core 7.0.10, spring-security 7.1.2, tomcat-embed-core 11.0.27, hibernate-core 7.4.13.Final, jackson-databind 3.1.7, nimbus-jose-jwt 10.9.1, postgresql 42.7.14, `embedded-postgres` 2.1.0 (solo pruebas). **No se verificó si alguna tiene CVE.** | `pokedex-backend/pom.xml` · `.github/workflows/ci.yml` | Bajo | `.\mvnw.cmd dependency:list -DincludeScope=runtime`. | Activar Dependabot (Maven y npm) y/o `org.owasp:dependency-check-maven` en el CI. |
| S-19 | Las Actions del CI van por etiqueta (`actions/checkout@v4`, `docker/build-push-action@v6`…), no por SHA, y no hay análisis de secretos. | `pokedex-backend/.github/workflows/ci.yml:17,18,30-32` | Bajo | Leer el archivo. | Fijar por SHA o permitir solo acciones verificadas; añadir `gitleaks` en el CI. |
| S-20 | El login del frontend no comprueba `Origin` y depende solo de `sameSite=lax`. `proxy.ts` solo comprueba que exista la cookie (decisión documentada; el backend valida el token en cada escritura). | `pokedex-frontend/app/api/sesion/route.ts:23-50` · `proxy.ts:10` | Bajo | Leer el código. | Aceptable. Si se quiere endurecer, comprobar `Origin` en el Route Handler. |
| S-21 | Cabeceras de seguridad: no se revisó que Next/Vercel o Spring envíen CSP, `X-Content-Type-Options` ni `Referrer-Policy` (no hay configuración en el código). | `pokedex-frontend/next.config.ts` | Bajo | `curl.exe -I` sobre producción. | Añadir `headers()` en `next.config.ts` con CSP básica y las otras dos cabeceras. |

### 3.2 Revisado y sin hallazgo
- **CORS:** lista por `CORS_ORIGENES`, nunca `*`, sin credenciales, solo `/api/**` (`SeguridadConfig.java:119-129`).
- **JWT:** HS256 fijado, mínimo de 32 bytes, sin valor por defecto, expiración 120 min.
- **Rutas:** `permitAll` solo para `OPTIONS`, `/error`, `POST /auth/login`, `POST /pokemons/identificar` y `GET /**`; todo lo demás es ADMIN (`SeguridadConfig.java:57-66`).
- **Subida de foto:** límites de Spring 5 MB / 6 MB, validación por magic bytes antes de llamar a la IA, sin guardar el archivo.
- **Errores:** 500 genérico, mismo JSON en `GlobalExceptionHandler` y `ErroresSeguridad`.
- **Secretos:** `.gitignore` cubre `application-local.properties`; el Dockerfile solo trae valores de relleno para el entrenamiento CDS; el Postman lleva variables vacías.
- **Frontend:** cookie `httpOnly` + `sameSite=lax` + `secure` en producción (`lib/sesion.ts:26-33`); Server Actions con sesión, `esId` y `esObjeto` (`app/admin/acciones.ts`); `destinoSeguro` (`lib/sesion-comun.ts:26-29`); único `dangerouslySetInnerHTML` con una cadena constante (`components/ui/ScriptEnLinea.tsx:11`).

## 4. Contrato (`docs/contratos/api.md`)

La API cumple el contrato en rutas, métodos y formatos. Los hallazgos son de documentación incompleta o de usos no cubiertos por el front.

| ID | Hallazgo | Archivo:línea | Sev. | Cómo comprobarlo | Corrección propuesta (sin aplicar) |
|---|---|---|---|---|---|
| C-01 | `DELETE /movimientos/{id}` dice solo «Eliminar». El código responde 204 y 404. `DELETE /rutas` y `DELETE /pokemons` sí lo documentan. | `docs/contratos/api.md:136` | Bajo | Comparar con `api.md:33,158`. | Escribir «204; 404 si no existe». |
| C-02 | `POST/PUT /movimientos` y `/rutas` no documentan el 201, el 404 ni el 400 con `campos`. Un nombre repetido responde **400** (`CamposInvalidosException`), no 409; la tabla de errores solo asigna el 409 a importación y Pokémon. | `api.md` (secciones Movimientos y Rutas) · `api.md:293` | Medio | `POST /api/movimientos` con un nombre existente: 400. | Documentar 201/404/400 y que el duplicado es 400, o cambiarlo a 409 por coherencia con `POST /pokemons`. |
| C-03 | Sin 415/406 en la tabla de errores (ver S-04). | `api.md:293` | Bajo | Leer la tabla. | Añadir las filas junto con S-04. <br>**Corregido** — grupo 1, commit «fix: seguridad del backend (S-01, S-02, S-03, S-04, S-08, S-09)». |
| C-04 | La sección «Ampliación F11–F15 (aprobada, pendiente de implementar)» incluye F15, que ya está implementada (autenticación y CRUD están en secciones propias). Los endpoints `/habilidades`, `/ubicaciones`, `/juegos`, `?juego=` y `?metodo=` no existen en el backend (B.33–B.38 abiertos). Es coherente con `tareas.md`. | `api.md:327-329,357` | Bajo | `grep -r "habilidades" src/main` sin resultados. | Renombrar a «F11–F13» y dejar F14/F15 como ya implementadas. |
| C-05 | El tipo `Pokemon` del frontend no tiene `editadoAMano`, que el contrato documenta como `true \| null`. El panel de administración no lo muestra. | `pokedex-frontend/lib/api/tipos.ts` | Bajo | `grep editadoAMano lib/api/tipos.ts` sin resultados. | Añadir `editadoAMano?: true \| null` si el panel lo va a usar; si no, dejarlo anotado. |
| C-06 | En las lecturas, `errorDe` descarta `campos` del 400. Solo las escrituras del panel (`admin.ts:45-46`) los conservan. | `pokedex-frontend/lib/api/http.ts:57-65` | Bajo | Leer el código. | Guardar `campos` en `ErrorApi` si alguna lectura los necesita. Hoy no hace falta. |
| C-07 | El frontend corta las fotos en 2 MB (`MAX_BYTES_FOTO`) y reduce a 1,5 MB; el backend acepta 5 MB. Es coherente (el límite de Vercel es 4,5 MB) y está explicado. | `lib/identificar-mensajes.ts:9` | Bajo | — | Ninguna; solo constancia. |
| C-08 | Sin pruebas de contrato en el CI: `contrato.test.ts` solo corre con `API_URL`, y el CI del frontend no lo define. | `pokedex-frontend/.github/workflows/ci.yml` · `lib/api/contrato.test.ts` | Medio | Ver el workflow: lint, test y build, sin `API_URL`. | Añadir un job que arranque el backend (perfil con PostgreSQL embebido o servicio) y lance `contrato.test.ts`. |

## 5. Specs: criterios de aceptación sin prueba automática

Se cruzó cada criterio con las pruebas del backend (`src/test`), del frontend (`*.test.ts(x)`) y de los E2E. Los datos reales de la PokeAPI solo se verifican a mano, como declara `docs/pruebas.md`.

| ID | Spec | Criterio sin prueba | Sev. | Cómo comprobarlo | Corrección propuesta (sin aplicar) |
|---|---|---|---|---|---|
| T-01 | F1 | `sort=nombre,asc` en el backend (solo lo prueba el mock del front, `mock.test.ts:36`) | Bajo | Buscar el criterio en `PokemonControllerTest`/`ApiConPostgresTest`. | Añadir prueba en `ApiConPostgresTest`. |
| T-02 | F2 | `obtencion/inicial` incluye bulbasaur, charmander, squirtle y pikachu. `obtencion/fosil` solo comprueba el 200 (`PC:232`), no la igualdad con `Fósil`. | Bajo | Idem. | Prueba con datos sembrados. |
| T-03 | F3, F10 | `totalElements ≥ 10` tras importar 10; repetir no duplica; `total=1025` con 0 fallidos | Bajo | Solo a mano. | Prueba del servicio con PokeAPI simulada para los dos primeros; el tercero queda manual. |
| T-04 | F4 | `size=500` → 200 y `sort=foo` → 400 en `/movimientos`; orden y `thunderbolt` en `/pokemons/25/movimientos`; PUT con potencia 100 → `esCompetitivo=true` | Medio | Idem. | Pruebas en `MovimientoControllerTest`/`ApiConPostgresTest`. |
| T-05 | F5 | `/regiones` exactamente las 9; `region/KANTO` = `kanto`; `sort=foo` en rutas; `region/kanto?size=200`; éxito del PUT sin tocar `idPokeapi`; `GET /api/tipos` en el backend | Medio | Idem. | Pruebas en `RutaControllerTest`/`ApiConPostgresTest`. |
| T-06 | F5 | «`DELETE /api/rutas/{id}` responde 405» está **obsoleto**: desde B.31 responde 204 (`RC:138`, `API:316`). Es un criterio que contradice el código. | Medio | `docs/specs/F5-rutas-regiones.md:25` frente a `api.md:158`. | Reescribir el criterio (204 y 404), no cambiar la prueba. <br>**Corregido** — grupo 3, commit «docs: specs, README y conteos de la interventoría (D-01, T-06, T-16, T-17, D-04, D-12)». |
| T-07 | F6 | Nombres concretos de ruta («Bosque Verde», «Calle Victoria», «Ruta 10») y ningún nombre con «(Kanto)» o «/» | Bajo | Solo a mano. | Prueba del mapeo WikiDex con esos tres casos. |
| T-08 | F7 | Pichu al pedir Pikachu (`/25`); Slowbro, Perrserker, Alcremie, Wormadam, Pawmot y Annihilape; `/abc/evoluciones` → 400. B.26 sigue abierto. | Medio | Solo a mano. | Pruebas de `EvolucionService` con esos casos y de `GET /pokemons/abc/evoluciones`. |
| T-09 | F9 | Arrancar dos veces el servidor sin error (extensión `unaccent`) | Bajo | Solo a mano. | Prueba de arranque doble sobre el PostgreSQL embebido. |
| T-10 | F11, F12, F13 | Todos los criterios (6 + 10 + 9). Están «Pendiente» y `tareas.md` los tiene abiertos: coherente. | Bajo | `Habilidad`, `Juego` y `Encuentro` no existen en `src/main`. | Implementar por spec en la siguiente fase. |
| T-11 | F14 | Criterio del front: selector «Tipo 2», chips, total igual al de la API. Solo está probado el parseo de la URL (`parametros-catalogo.test.ts:54,61`). | Bajo | Idem. | Prueba de componente o E2E del selector. |
| T-12 | F15 | Postman de punta a punta; `grep` sin credenciales en el repo; `/admin` sin sesión redirige a `/login` y vuelve; el 400 del servidor junto a su campo; build sin `API_URL`. Los E2E del admin (`admin.e2e.ts`) cubren solo login malo y bueno, y el segundo se omite sin `.env.test`. | Medio | `ls e2e` y `pruebas.md`. | Añadir el E2E de la redirección y la prueba de `acciones.ts`. |
| T-13 | F19 | Sin `API_URL` y sin scroll horizontal a 360 px en `/pokemon/*` y `/naturalezas`. `responsive.e2e.ts:6` solo cubre `/` e `/identificar`. | Bajo | Leer `responsive.e2e.ts`. | Añadir las dos rutas al bucle del E2E. |
| T-14 | F20 | «Sin rastro» (la foto no se guarda en disco ni en BD, ni va al log). Está declarado como comprobación manual por diseño. | Bajo | Revisar el log tras una identificación. | Prueba que capture el log (`OutputCaptureExtension`) y verifique que no contiene los bytes ni el Base64. |
| T-15 | Front | Sin pruebas unitarias: `app/api/sesion/route.ts`, `proxy.ts`, `app/admin/acciones.ts`, `lib/api/admin.ts`, `lib/sesion.ts`, `lib/api/http.ts` (solo lo cubre `contrato.test.ts` con `API_URL`). | Medio | `ls lib/api/*.test.ts`. | Pruebas con `fetch` simulado para esas rutas y acciones. |
| T-16 | F5, F7, F14, F15, F20 | **Estado del spec contradice la realidad.** F5, F7 y F14 dicen «Implementada · criterios con pruebas automáticas» y no es cierto (T-05/06/08/11). F15 dice «En progreso (falta el frontend, FE.34–FE.37)» y `tareas.md` los marca hechos. F20 dice «Pendiente» y B.43–B.47 y FE.49–FE.51 están hechos. | Medio | Cabecera `**Estado:**` de cada spec frente a `tareas.md`. | Actualizar los estados y marcar las casillas de criterios con prueba. <br>**Corregido** — grupo 3, commit «docs: specs, README y conteos de la interventoría (D-01, T-06, T-16, T-17, D-04, D-12)». |
| T-17 | Docs | Conteo de pruebas: `CLAUDE.md:45` dice **210**; hay **205** `@Test` en 20 clases (sin `@ParameterizedTest`). `tareas.md` B.39 (total de las dos suites) sigue sin cerrarse. | Bajo | `grep -c "@Test" -r src/test` suma 205. Confirmar con `.\mvnw.cmd test`. | Corregir con el total que dé Maven y cerrar B.39. <br>**Corregido** — grupo 3, commit «docs: specs, README y conteos de la interventoría (D-01, T-06, T-16, T-17, D-04, D-12)». |

**Resumen por spec:** F1–F10 y F19 tienen la mayoría de sus criterios cubiertos; F11–F13 no tienen ninguno (por diseño); F20 los cubre todos salvo «sin rastro».

## 6. Calidad

| ID | Hallazgo | Archivo:línea | Sev. | Cómo comprobarlo | Corrección propuesta (sin aplicar) |
|---|---|---|---|---|---|
| Q-01 | `PokemonService` tiene 1282 líneas y mezcla importación, cadenas evolutivas, forma de obtención y mapas de traducción. | `pokedex-backend/.../service/PokemonService.java` | Medio | `wc -l`. | Separar en `ImportacionService`, `EvolucionImportador` y `FormaObtencion` (sin cambiar comportamiento, con las pruebas actuales como red). |
| Q-02 | Los 18 tipos están duplicados: `MovimientoController.TIPOS` y `TiposEspanol`. | `MovimientoController.java:24,128` | Bajo | `grep -rn "\"fighting\"" src/main`. | Una sola constante compartida. |
| Q-03 | `MAX_GENERACION = 9` repetido en `PokemonController` y `DatosPokemon`; `MAX_MOVIMIENTOS = 900` y `PokemonService.MAX_POKEMON` en otra clase. | `PokemonController.java:33,143` | Bajo | `grep -rn MAX_GENERACION src/main`. | Constantes en un único lugar. |
| Q-04 | Errores de petición inconsistentes: `BadRequestException` (400 simple) frente a `CamposInvalidosException` (400 con `campos`) según el controller. Movimientos y rutas usan la segunda; `AuthController` y los filtros de Pokémon, la primera. | `GlobalExceptionHandler.java:50,57` · `AuthController.java:48` | Bajo | Comparar los JSON de un 400 de cada endpoint. | Decidir una regla (campos en cuerpo, mensaje en parámetros) y escribirla en `docs/decisiones.md`. |
| Q-05 | Validación duplicada: `nombre.isBlank()` y `buscar(id)` + `ResourceNotFoundException` repetidos entre controllers. | `PokemonController`, `MovimientoController` | Bajo | Leer ambos. | Método común o `@ControllerAdvice` para el 404. |
| Q-06 | Comentario obsoleto: dice que el 429 «se traduce en la tarea 2.5 (todavía no hay controlador ni manejador)» y el handler ya existe. | `exception/LimiteExcedidoException.java:4` | Bajo | `GlobalExceptionHandler.java:112`. | Quitar la frase. |
| Q-07 | Nombres confusos: `cifradorContrasenas` (BCrypt es un hash, no cifrado), `Usuario.contrasena` guarda un hash, `importarTodoAutomaticamente` solo importa 50 + 50, y `Pokemon.region` es la región de origen. | `SeguridadConfig.java:114` · `PokemonController.java:247` | Bajo | Leer. | Renombrar (`codificadorContrasenas`, `contrasenaHash`, `importarMuestra`) con su migración de columna. |
| Q-08 | `ddl-auto=update` y `spring.sql.init.mode=always`, sin migraciones versionadas. Ya está en la lista del equipo (frente a Flyway). | `application.properties:45` | Bajo | Leer el archivo. | **Pendiente justificado:** pasar a Flyway antes de tener datos que no se puedan regenerar. |
| Q-09 | Frontend: `apiDisponible()` y `adminDisponible()` son idénticas; la segunda solo envuelve a la primera. | `lib/sesion-comun.ts:13-19` | Bajo | Leer. | Dejar una y exportar un alias, o usar solo `apiDisponible`. |
| Q-10 | Frontend: cuatro estilos de error (`ErrorApi`, `RespuestaEscritura`, `Error` genérico en `admin.ts:57` y `error()` de `Response` en los Route Handlers). | `lib/api/http.ts` · `lib/api/admin.ts` · `app/api/*` | Bajo | Comparar. | Unificar la lectura y la escritura del panel en un solo tipo de resultado. |
| Q-11 | Frontend: botones con `min-h-9 … sm:min-h-9` (el breakpoint repite el mismo valor). | `components/admin/BotonEliminar.tsx:51` · `BotonCerrarSesion.tsx:23` · `app/admin/*/page.tsx:77,93` | Bajo | Leer. | Quitar el `sm:` redundante al corregir A-01. <br>**Corregido** — grupo 4, commit «fix: accesibilidad táctil y aria-label (A-01, A-02)». |
| Q-12 | `next.config.ts` usa opciones experimentales (`agentFeedback`, `cacheComponents`, `partialPrefetching`) que dependen de la versión de Next. | `pokedex-frontend/next.config.ts` | Bajo | Leer. | Anotar la dependencia en el README o revisar en cada subida de versión. |
| Q-13 | Las tres familias tipográficas se cargan de Google Fonts (Space Grotesk, Inter, JetBrains Mono). | `pokedex-frontend/app/layout.tsx` | Bajo | `npm run build` y medir. | Reducir a dos o usar `next/font` con subconjuntos. |

## 7. Accesibilidad y responsive (frontend)

Revisión de código de las pantallas principales (catálogo, ficha, movimientos, regiones, rutas, naturalezas, identificar, login, admin). `docs/mediciones.md` registra Lighthouse de accesibilidad 100 en 6 mediciones; no se repitieron. Se confirmó: `<html lang="es">`, enlace «Saltar al contenido» (`Cabecera.tsx:12`), `:focus-visible` global (`app/globals.css:44-46`), un `h1` por pantalla, `<caption class="sr-only">` y `scope` en las tablas, `aria-invalid` + `aria-describedby` en los formularios, `aria-live` en `Paginacion` y `BotonGrito`.

| ID | Hallazgo | Archivo:línea | Sev. | Cómo comprobarlo | Corrección propuesta (sin aplicar) |
|---|---|---|---|---|---|
| A-01 | Áreas táctiles por debajo de 44 px: `min-h-8` (32 px) en el chip y en «Quitar filtros»; `min-h-9` (36 px) en los botones del panel. | `components/ui/Chip.tsx:15` · `app/page.tsx:88` · `components/admin/BotonEliminar.tsx:51` · `app/admin/pokemon/page.tsx:77` | Medio | Lighthouse «tap targets» o inspeccionar a 360 px. | Subir a `min-h-11` en móvil (como ya hace `Campo.tsx:4`). <br>**Corregido** — grupo 4, commit «fix: accesibilidad táctil y aria-label (A-01, A-02)». |
| A-02 | `aria-label` sobre un `<span>` sin rol: no se anuncia de forma fiable. | `components/pokemon/TablaMovimientos.tsx:43,49` · `app/naturalezas/page.tsx:45,55` | Medio | Lector de pantalla sobre la celda «—». | Usar `<span class="sr-only">Sin tipo</span>` junto al guion con `aria-hidden`. <br>**Corregido** — grupo 4, commit «fix: accesibilidad táctil y aria-label (A-01, A-02)». |
| A-03 | La tabla de naturalezas no tiene contenedor `overflow-x-auto`. Solo tiene 3 columnas. | `app/naturalezas/page.tsx:22` | Bajo | Ver a 320 px. | Envolverla como las otras tablas. |
| A-04 | `--color-borde: #8A93A6` sobre blanco da ≈3,2:1. Cumple 3:1 para componentes de UI, no para texto. | `app/globals.css` | Bajo | Calcular el contraste. | Confirmar que no se usa como color de texto. |
| A-05 | `docs/mediciones.md` no midió los formularios de `/admin` con datos (línea 47), ni los E2E cubren teclado completo (`Tab`/`Enter`/`Escape`). | `docs/mediciones.md:47` | Bajo | Leer. | Medir `/admin` con API y añadir el E2E de teclado. |

## 8. Documentación desactualizada

| ID | Hallazgo | Archivo:línea | Sev. | Cómo comprobarlo | Corrección propuesta (sin aplicar) |
|---|---|---|---|---|---|
| D-01 | Los comandos de importación del README usan `GET …/importar-rango` **sin token**; la ruta exige ADMIN y devuelve 401. El README tampoco menciona `JWT_SECRETO`, sin el cual el servidor no arranca. `CLAUDE.md` usa `POST` pero tampoco menciona el token. | `pokedex-backend/README.md:19` | Alto | Seguir el README en limpio. | Documentar el login, el uso de `Authorization: Bearer` y las variables `JWT_SECRETO` y `ADMIN_*`. <br>**Corregido** — grupo 3, commit «docs: specs, README y conteos de la interventoría (D-01, T-06, T-16, T-17, D-04, D-12)». |
| D-02 | `CLAUDE.md` dice que `POST /api/pokemons/identificar` «todavía no está en el código, se declara en la tarea 2.1». Ya está implementado y declarado. | `pokedex-backend/CLAUDE.md:90` · `SeguridadConfig.java:64` | Medio | Comparar. | Quitar la frase. |
| D-03 | `CLAUDE.md` dice «las specs F1–F10 están todas implementadas». F14, F15 y F20 también lo están, y F11–F13 siguen pendientes. | `CLAUDE.md:126` | Medio | Comparar con `tareas.md`. | «F1–F10, F14, F15, F19, F20; pendientes F11–F13». |
| D-04 | `CLAUDE.md` dice «210 pruebas»; hay 205 `@Test`. | `CLAUDE.md:45` | Bajo | Ver T-17. | Corregir con el total real. <br>**Corregido** — grupo 3, commit «docs: specs, README y conteos de la interventoría (D-01, T-06, T-16, T-17, D-04, D-12)». |
| D-05 | La sección de estructura de `CLAUDE.md` omite `IdentificacionController`, `HealthController`, `VisionConfig`, `IdentificacionService`, `ServicioVision*`, `ValidadorFoto`, `LimiteIdentificaciones`, `SimilaresService` y las excepciones de F20. | `CLAUDE.md` (Estructura) | Bajo | Listar `src/main`. | Añadirlos. |
| D-06 | `docs/arquitectura.md` describe `config/` solo como «CORS»; no menciona Spring Security, JWT, visión, ni los códigos 401/403/422/429/502. | `docs/arquitectura.md` | Bajo | Leer. | Actualizar la tabla de capas y de errores. |
| D-07 | `docs/pruebas.md` no lista `GestionPokemonControllerTest`, `HealthControllerTest`, `ManejoMultipartTest`, `IdentificacionApiTest`, `IpCliente*Test`, `ValidadorFotoTest`, `LimiteIdentificacionesTest` ni `ServicioVision*Test`, y la tabla E2E omite pruebas (hay 11, FE.51 dice 8). | `docs/pruebas.md` | Bajo | Listar `src/test`. | Completar las tablas. |
| D-08 | `BITACORA_IA.md` dice que `CLAUDE.md` «todavía dice 107 pruebas»; ya dice 210. | `BITACORA_IA.md:387` | Bajo | Leer. | Quitar o fechar la línea. |
| D-09 | **Comentario de `lib/api/http.ts`:** `TIEMPO_ESPERA_MS = 180_000` y su comentario (122,3 s → 47,9 s con CDS) son coherentes. Pero el de `TIEMPO_ESPERA_IDENTIFICAR_MS` dice «hasta ~135 s medidos», cifra **anterior** a la mejora con CDS (116 y 134 s en dev); el arranque actual medido es 47,9 s. Además, 180 s por lectura en serie podría superar los 300 s de Vercel en una página con varias llamadas. | `pokedex-frontend/lib/api/http.ts:43-54` | Medio | Leer el comentario y `docs/despliegue.md`. | Actualizar el número con la medición vigente y dejar claro que 135 s es la medición antigua; comprobar cuántas lecturas en serie hace la página más pesada. <br>**Corregido** — grupo 3, commit «docs: comentario de http.ts con la medición vigente (D-09)» del repo del frontend. |
| D-10 | README del frontend: la lista de pantallas omite `/naturalezas`, `/identificar`, `/login` y `/admin`; la estructura omite `components/admin`, `app/api` y `e2e`; los comandos omiten `test:e2e`; dice **Node 20** y el CI usa **22**; dice «specs F1–F10» y existen F11–F20; no documenta `DEV_ORIGINS` ni `E2E_ADMIN_*`. | `pokedex-frontend/README.md:15,80` · `.github/workflows/ci.yml:21` | Medio | Leer. | Actualizarlo. |
| D-11 | `CLAUDE.md` del frontend: la sección de estructura omite las pantallas nuevas, duplica `components/pokemon/` y no lista `lib/parametros-url.ts` ni `lib/recursos-pokemon.ts`; «F1–F10» desfasado. | `pokedex-frontend/CLAUDE.md` | Bajo | Leer. | Actualizarlo. |
| D-12 | Conteos del frontend: `tareas.md` declara 140 pruebas de Vitest, 8 E2E y 23 del mock; hay unas 143 llamadas a `it` (más los `it.each`), 11 E2E y 27 en `mock.test.ts`. El CI del frontend no ejecuta E2E ni `contrato.test.ts`. | `docs/tareas.md` (FE.6, FE.50, FE.51) | Bajo | `grep -c "it(" …` y `ls e2e`. | Corregir con los totales de `npm run test` y cerrar B.39. <br>**Corregido (solo los conteos)** — grupo 3, mismo commit de docs; el CI sigue sin ejecutar E2E ni contrato y B.39 sigue abierto. |
| D-13 | `docs/mediciones.md` es del 2026-11-17 y se midió sin `API_URL`. | `pokedex-frontend/docs/mediciones.md` | Bajo | Leer. | Repetir con la API real cuando se despliegue. |

## 9. Pendientes justificados
- **`ddl-auto=update` frente a Flyway** (Q-08): se mantiene mientras la base se pueda regenerar con la importación.
- **Revocación de tokens** (S-12): un solo administrador y expiración corta.
- **`open-in-view`** (S-14): documentado en `decisiones.md`.
- **Límite de uso en memoria por instancia:** ya figura como limitación conocida en `decisiones.md`.

## 10. Orden de corrección sugerido (tarea 4.2)
1. **S-01** (límite de login) y **D-01** (README de importación).
2. **S-02**, **S-03** y **S-04** juntos (validación de entrada y errores): comparten el `GlobalExceptionHandler`.
3. **S-06**, **S-07**, **S-08** y **S-09**.
4. **S-17** (subir `vitest`, con las pruebas verdes) y **S-18** (analizar dependencias del backend).
5. **C-02**, **C-08**, **T-04** a **T-06**, **T-08**, **T-12**, **T-15** y **T-16**.
6. **A-01** y **A-02**.
7. Documentación (**D-02** a **D-13**) y los hallazgos Bajo restantes, en un solo commit de documentación.

Cada corrección con su causa raíz, su prueba de regresión y su commit `fix: … (closes #n)`, sin tocar una prueba para que pase.
