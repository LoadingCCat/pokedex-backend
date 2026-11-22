# Pruebas · Pokedex+

Cómo se comprueban los criterios de aceptación de `docs/specs/` (B.19).

```powershell
.\mvnw.cmd test        # todas las pruebas; no necesita el PostgreSQL local ni la PokeAPI
```

- La primera vez Maven descarga un PostgreSQL embebido (zonky, unos 30 MB). Después corre sin internet.
- Se lanza desde una terminal normal (PowerShell, VS Code). Desde el Bash del agente el PostgreSQL embebido no puede abrir su puerto, igual que el servidor.

## Niveles

| Nivel | Clases | Base de datos | Qué prueba |
|---|---|---|---|
| Controllers | `PokemonControllerTest`, `MovimientoControllerTest`, `RutaControllerTest`, `SeguridadControllerTest` (`@WebMvcTest` con `@ConSeguridad`) | Ninguna: repositorios y servicios simulados | Validaciones (400), 404, 405, 409, 202, formato `{ content, page }`, página de 20 / máximo 200, orden por defecto, formato de errores, 500 sin detalles |
| Servicios | `PokemonServiceTest`, `EvolucionServiceTest`, `EvolucionCondicionesTest`, `LugaresEnEspanolTest`, `ProgresoImportacionTest`, `AdministradorInicialTest` | Ninguna: PokeAPI y repositorios simulados | Importación (datos, nombre en español, forma de obtención, timeouts), B.18, F10 (estados, 409, errores), árbol de evoluciones |
| API + SQL | `ApiConPostgresTest`, `ImportacionConPostgresTest`, `ArranqueDobleTest` (`@SpringBootTest`) | PostgreSQL 14 embebido (`PostgresEmbebido`) | Consultas reales: `unaccent`, `Specification` con `EXISTS`, región sin duplicados, `countQuery`, `sort` inválido en `@Query`, `schema.sql` y arranque de la aplicación |

**Seguridad en las pruebas (desde B.30):** los `@WebMvcTest` cargan las reglas reales con `@ConSeguridad`, que importa `SeguridadConfig` y fija un secreto de JWT que solo existe en las pruebas. Las pruebas de F1–F14 que escriben (POST, PUT, DELETE, importar) corren con `@WithMockUser(roles = "ADMIN")` a nivel de clase: es el único cambio en ellas, y siguen comprobando lo mismo. Lo que pasa sin sesión o sin rol se prueba aparte (`SeguridadControllerTest` y los casos `@WithAnonymousUser`). `ApiConPostgresTest` define su propio administrador de prueba. En B.32, `metodoNoPermitidoResponde405` cambió de ejemplo (de `DELETE /pokemons/25`, que ahora existe, a `DELETE /pokemons/25/movimientos`): sigue comprobando el 405.

No se usa H2: no tiene `unaccent` y obligaría a probar consultas distintas a las de PostgreSQL. No se usa Testcontainers porque necesita Docker.

## Criterios cubiertos

| Spec | Pruebas automáticas | Solo a mano (necesitan la PokeAPI real) |
|---|---|---|
| F1 Catálogo | Listado paginado y ordenado, máximo 200, `sort=nombre,asc` en SQL (T-01), ficha con `totalEstadisticas`, sin `movimientos`, 404, id no numérico, URL inexistente, 405, 500 | — |
| F2 Filtros | Generación 0/10 → 400, `obtencion` con o sin tilde e inválida, tipo sin mayúsculas, región con nativos + rutas sin duplicados, ruta inexistente → 404, `obtencion/inicial` con los cuatro iniciales y `fosil` = `Fósil` (T-02) | — |
| F3 Importación | 400 de `total`/`movimientos`, 409, timeout = fallido y sigue, datos y estadísticas, Inicial y Evolución, conserva rutas si `/encounters` falla | Importar `total=1025` de verdad contra la PokeAPI (0 fallidos). Con la PokeAPI simulada sí hay prueba (T-03): 10 y 1025 sin fallidos (`PokemonServiceTest`) y, con PostgreSQL embebido, `totalElements ≥ 10` y repetir no duplica (`ImportacionConPostgresTest`) |
| F4 Movimientos | Listado por nombre (`size=500` → 200 resultados como máximo; `sort=foo` también en `/buscar`; T-04), `/pokemons/25/movimientos` en orden alfabético con `thunderbolt`, 404, búsqueda vacía → 400, crear (201, `esCompetitivo`, categoría normalizada, id ignorado), datos inválidos, repetido, PUT, DELETE desvincula, competitivos y categoría en SQL; `tipo` en minúsculas, tipo inválido → 400, PUT sin `tipo` lo conserva, importado con su tipo y reimportación que solo llena `tipo` si estaba en `null` | — |
| F5 Rutas | Listado por nombre, las 9 regiones exactas, `region/KANTO` = `region/kanto`, `region/kanto?size=200`, PUT que no toca `idPokeapi`, `GET /tipos` con `electric` (T-05), 404, POST fuerza `idPokeapi` a `null`, validaciones, repetida, PUT 404, rutas por región y Pokémon de una ruta en SQL | — |
| F6 Nombres en español | Búsqueda en `nombre` y `nombreEs` (SQL), `nombreEs` en blanco → `null`, PUT conserva `nombreEs`, reimportar solo cambia `nombreEs` (B.18); lugares (B.28): nombre de WikiDex si la PokeAPI no lo trae en español, la PokeAPI tiene prioridad, una ruta con el nombre en inglés pasa al español y una renombrada con PUT no se toca; «Bosque Verde», «Calle Victoria» y «Ruta 10» de Kanto y ningún nombre con «(Kanto)» ni «/» (T-07: `LugaresEnEspanolTest`) | Que la PokeAPI traiga "Pikachu" y "Puño Trueno" tras reimportar |
| F7 Cadena evolutiva | Árbol desde la base, Eevee con 8 ramas ordenadas, igual desde cualquier etapa, Pichu como raíz al pedir a Pikachu, `/abc/evoluciones` → 400, las condiciones de Slowbro, Perrserker, Alcremie, Wormadam, Pawmot y Annihilape con el movimiento ya en la base (T-08: `EvolucionCondicionesTest`), etapa no importada con `null`, sin cadena → una etapa, 404 | Textos de `condicion` armados con la PokeAPI real. T-18 (corregido): en una importación en limpio, las condiciones que nombran un movimiento aún no importado se vuelven a armar al terminar los Pokémon (`EvolucionCondicionesTest.annihilape…EnUnaImportacionEnLimpio`) |
| F8 Filtros combinados | Filtros vacíos ignorados, combinación con Y, con `sort`, generación/obtención inválidas → 400, tipo/región inexistentes → vacío, igual que el endpoint de un filtro | — |
| F15 Autenticación (B.30) | Login correcto (el token sirve para escribir), contraseña mala y usuario inexistente → 401 con el mismo mensaje, campos vacíos o sin cuerpo → 400, escribir sin token → 401 con el formato de error y `WWW-Authenticate`, token sin ADMIN → 403, token vencido → 401, firmado con otro secreto o mal formado → 401, GET públicos sin token, importación por GET sin token → 401, administrador inicial con BCrypt (se crea; no se toca si existe; no se crea sin datos), de punta a punta con PostgreSQL (login → POST /rutas); B.31: borrar una ruta importada la desvincula y la excluye, una creada a mano no se excluye, 404, sin sesión → 401, de punta a punta con PostgreSQL (Pikachu pierde el Bosque), y la reimportación no vuelve a crear un lugar excluido; B.32: editar guarda y marca `editadoAMano`, estadística en 300 o "abc", `ps` 0 y peso negativo → 400 con `campos`, vacíos, obtención y tipos desconocidos, más de 2 tipos y rutas inexistentes → 400 con su campo, 404, sin cuerpo → 400, crear (201, región según la generación, sale de los excluidos), número ocupado → 409, crear sin número, slug ni generación, borrar (204, excluido), 404, sin sesión → 401, la reimportación salta un excluido y conserva uno editado, `campos` en los 400 de movimientos y rutas, y de punta a punta con PostgreSQL (PUT con 2 tipos y rutas conservadas; DELETE de Cyndaquil quita sus filas de `pokemon_tipo` y `pokemon_ruta`) | Arrancar el servidor real sin `JWT_SECRETO` (no arranca); el Postman de punta a punta. Front (T-12, T-15): `acciones.test.ts` (el 400 del servidor devuelve `campos`, 401 → login), `proxy.test.ts`, `route.casos.test.ts`, `sesion.test.ts`, `admin.escritura.test.ts`, y los E2E de la redirección. Sin probar: que `/login` y `/admin` muestren el aviso en un build sin `API_URL` (solo lo comprueba que el build del CI no falle) |
| F14 Dos tipos | Front (T-11): selector «Tipo 2» desactivado/activo y sin el tipo ya elegido (`FiltrosCatalogo.test.tsx`); E2E de la URL, los chips y el total igual al de la API (`catalogo.e2e.ts`). Back: `?tipo=fire&tipo=flying` = los que tienen ambos (SQL), orden de los tipos indiferente, repetidos y vacíos cuentan una vez, igual que `/tipo/{t}` con uno, combinado con región, generación, obtención y `sort` sin duplicados, 3 tipos → 400 | — |
| F9 Sin tildes | `psiquico`, `psíquico`, `puno` = `puño` (con `totalElements`), `pikachú`, `flabébé`, filtro combinado, extensión creada por `schema.sql`, arranque doble de la aplicación sobre el mismo PostgreSQL (T-09: `ArranqueDobleTest`) | — |
| F10 Segundo plano | 202 al instante, 409 mientras corre, `terminada` con los 5 resultados, aceptar otra al terminar, `error` libera el bloqueo, ejecutor que rechaza la tarea, estados y porcentaje de `ProgresoImportacion` | La importación real de las 1025 en segundo plano |

## F20 · Identificar por foto (B.43–B.47)

La visión va simulada (`ServicioVisionMock`, perfil `e2e`): las pruebas no salen a internet ni gastan cuota. `IdentificacionApiTest` (`@SpringBootTest` + MockMvc + PostgreSQL embebido) prueba el endpoint de punta a punta; cada prueba usa su propia IP, porque el límite es un contador único de la aplicación. MockMvc no aplica los límites de tamaño de Tomcat, así que `MaxUploadSizeExceededException`, `MultipartException` y `MissingServletRequestPartException` se prueban llamando al manejador (`ManejoMultipartTest`).

| Criterio de F20 | Prueba automática |
|---|---|
| Éxito: 200 con el Pokémon de PostgreSQL y 3 similares distintos | `IdentificacionApiTest.exitoDevuelveElIdentificadoYTresSimilaresDistintosConSuMotivo` (forma `tipos: [{ id, nombre }]`, `motivo`, la visión se llamó 1 vez) |
| Similares bien elegidos (mismo tipo, de menor a mayor diferencia) | `IdentificacionApiTest.similaresComparTenTipoYVanDeMenorAMayorDiferenciaDeTotal`; la consulta, en `ApiConPostgresTest` (`charmanderTiene3SimilaresConTipoCompartido…`, `losPokemonConEstadisticasIncompletas…`) |
| Sin token → 200 | `IdentificacionApiTest.sinTokenResponde200`; la regla pública, en `HealthControllerTest` |
| Sin foto o vacía → 400, sin llamar a la visión | `IdentificacionApiTest`: `sinElCampoFotoResponde400`, `conUnCampoConOtroNombreResponde400`, `fotoVaciaResponde400`; `ManejoMultipartTest` (parte ausente y multipart mal formado) |
| Formato inválido (PDF, `.txt` renombrado) → 400, sin llamar a la visión | `IdentificacionApiTest`: `pdfResponde400`, `txtRenombradoAJpgResponde400AunqueDigaImageJpeg`; `ValidadorFotoTest` (por los primeros bytes) |
| Más de 5 MB → 400, no 500 | `IdentificacionApiTest.fotoDeMasDe5MbResponde400NoUn500`; `ManejoMultipartTest.subidaMasGrandeQueElLimiteDa400` (corte de Tomcat) |
| No hay Pokémon o confianza < 0,6 → 422 | `IdentificacionApiTest`: `sinPokemonEnLaFotoResponde422`, `confianzaDe05Responde422`; el borde 0,6 sí pasa (`confianzaExactamenteEnElUmbralSeAcepta`) |
| Slug que no está en la base → 422 | `IdentificacionApiTest.slugQueNoExisteEnLaBaseResponde422`; con mayúsculas y tildes sí se encuentra (`elSlugSeBuscaSinImportarMayusculasNiTildes`) |
| Falla externa → 502 genérico | `IdentificacionApiTest.siLaVisionFallaResponde502ConMensajeGenerico`; el cliente de Gemini (timeouts, clave, respuesta cruda fuera del mensaje), en `ServicioVisionGeminiTest` |
| Abuso: la petición 11 → 429, sin llamar a la visión | `IdentificacionApiTest.laPeticion11DeLaMismaIpResponde429YNoLlamaALaVision` (la visión se llamó 10 veces, no 11) y `elLimiteEsPorIpYOtraIpSigueFuncionando`; la ventana, en `LimiteIdentificacionesTest` |
| Sin rastro (foto no guardada, log sin imagen) | El log (T-14): `IdentificacionApiTest.elLogNoContieneLaFotoNiEnExitoNiEnError` captura la salida y busca los bytes y su Base64, en éxito y en error. Disco y base de datos: por diseño el servicio solo lee los bytes en memoria y no hay tabla de fotos; **sin prueba automática** (MockMvc no pasa por Tomcat y no ve el archivo temporal de la subida) |

Los criterios "solo a mano" se comprueban con `curl.exe` como dice cada spec (ver `CLAUDE.md`).

## E2E con Playwright (FE.51)

Las pruebas de extremo a extremo viven en el frontend (`pokedex-frontend/e2e/`, archivos `*.e2e.ts`) y recorren el sitio real en Chromium contra este backend. **No** se ejecutan con `.\mvnw.cmd test` ni con `npm run test` (Vitest); se lanzan con `npm run test:e2e`.

**Levantar el backend para las E2E** (en la terminal de la app, no desde el Bash del agente), con el perfil `e2e`, que reemplaza a Gemini por `ServicioVisionMock`: no sale a internet ni gasta cuota, y siempre identifica a **Charmander** (confianza 0,93):

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=e2e"   # http://localhost:8080
```

Necesita la BD local con los Pokémon importados (Charmander y al menos 3 similares) y `JWT_SECRETO`, `ADMIN_USERNAME` y `ADMIN_PASSWORD` en `application-local.properties`. El front lo levanta Playwright (`webServer`, con `API_URL=http://localhost:8080/api`) o reutiliza el que ya corra con esa variable. Las credenciales del admin para la prueba de login correcto van en `pokedex-frontend/.env.test` (`E2E_ADMIN_USUARIO`, `E2E_ADMIN_CONTRASENA`; fuera de Git); sin ellas, esa prueba se salta.

El límite de 10 fotos por minuto por IP (F20) también aplica: una corrida envía 3 fotos; repetirla varias veces seguidas puede dar 429 (esperar un minuto).

| Recorrido | Archivo | Criterio que cubre |
|---|---|---|
| Buscar "charmander" en el catálogo y abrir su ficha (`/pokemon/4`) | `catalogo.e2e.ts` | Catálogo P1: buscador por nombre; tarjeta → ficha |
| Identificar, camino feliz: foto pequeña → Charmander + 3 similares que enlazan a su ficha | `identificar.e2e.ts` | F20 front: 200 con el identificado y 3 tarjetas, cada una lleva a su ficha |
| Identificar con error: un `.txt` da «No pudimos leer esta foto…» y «Probar con otra foto» | `identificar.e2e.ts` | F20 front: errores sin textos técnicos, con reintento |
| Identificar con foto grande (> 5 MB, generada en la prueba): la petición pesa < 1,5 MB | `identificar.e2e.ts` | F20 front: la foto se reduce en el navegador |
| 360 px: `/identificar`, `/`, `/pokemon/25`, `/pokemon/6` y `/naturalezas` sin scroll horizontal | `responsive.e2e.ts` | F20 front, F19 (T-13) y RNF-03 |
| Login con credenciales malas → «Usuario o contraseña incorrectos.»; con las buenas (si hay `.env.test`) → `/admin`; `/admin` y `/admin/pokemon` sin sesión → `/login?volver=…` y, con las buenas, se vuelve a la subpágina pedida | `admin.e2e.ts` | F15: login y redirección (T-12) |
| `/?tipo=fire&tipo=flying`: Charizard y Moltres sí, Charmander y Pidgey no, dos chips y el total de la API; «Tipo 2» desactivado hasta elegir el primero | `catalogo.e2e.ts` | F14 front (T-11) |
