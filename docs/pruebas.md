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
| Servicios | `PokemonServiceTest`, `EvolucionServiceTest`, `ProgresoImportacionTest`, `AdministradorInicialTest` | Ninguna: PokeAPI y repositorios simulados | Importación (datos, nombre en español, forma de obtención, timeouts), B.18, F10 (estados, 409, errores), árbol de evoluciones |
| API + SQL | `ApiConPostgresTest` (`@SpringBootTest`) | PostgreSQL 14 embebido (`PostgresEmbebido`) | Consultas reales: `unaccent`, `Specification` con `EXISTS`, región sin duplicados, `countQuery`, `sort` inválido en `@Query`, `schema.sql` y arranque de la aplicación |

**Seguridad en las pruebas (desde B.30):** los `@WebMvcTest` cargan las reglas reales con `@ConSeguridad`, que importa `SeguridadConfig` y fija un secreto de JWT que solo existe en las pruebas. Las pruebas de F1–F14 que escriben (POST, PUT, DELETE, importar) corren con `@WithMockUser(roles = "ADMIN")` a nivel de clase: es el único cambio en ellas, y siguen comprobando lo mismo. Lo que pasa sin sesión o sin rol se prueba aparte (`SeguridadControllerTest` y los casos `@WithAnonymousUser`). `ApiConPostgresTest` define su propio administrador de prueba. En B.32, `metodoNoPermitidoResponde405` cambió de ejemplo (de `DELETE /pokemons/25`, que ahora existe, a `DELETE /pokemons/25/movimientos`): sigue comprobando el 405.

No se usa H2: no tiene `unaccent` y obligaría a probar consultas distintas a las de PostgreSQL. No se usa Testcontainers porque necesita Docker.

## Criterios cubiertos

| Spec | Pruebas automáticas | Solo a mano (necesitan la PokeAPI real) |
|---|---|---|
| F1 Catálogo | Listado paginado y ordenado, máximo 200, ficha con `totalEstadisticas`, sin `movimientos`, 404, id no numérico, URL inexistente, 405, 500 | — |
| F2 Filtros | Generación 0/10 → 400, `obtencion` con o sin tilde e inválida, tipo sin mayúsculas, región con nativos + rutas sin duplicados, ruta inexistente → 404 | — |
| F3 Importación | 400 de `total`/`movimientos`, 409, timeout = fallido y sigue, datos y estadísticas, Inicial y Evolución, conserva rutas si `/encounters` falla | Importar `total=10` y `total=1025` de verdad (0 fallidos), repetir sin duplicar |
| F4 Movimientos | Listado por nombre, 404, búsqueda vacía → 400, crear (201, `esCompetitivo`, categoría normalizada, id ignorado), datos inválidos, repetido, PUT, DELETE desvincula, competitivos y categoría en SQL; `tipo` en minúsculas, tipo inválido → 400, PUT sin `tipo` lo conserva, importado con su tipo y reimportación que solo llena `tipo` si estaba en `null` | — |
| F5 Rutas | Listado por nombre, regiones como arreglo, 404, POST fuerza `idPokeapi` a `null`, validaciones, repetida, PUT 404, rutas por región y Pokémon de una ruta en SQL | — |
| F6 Nombres en español | Búsqueda en `nombre` y `nombreEs` (SQL), `nombreEs` en blanco → `null`, PUT conserva `nombreEs`, reimportar solo cambia `nombreEs` (B.18); lugares (B.28): nombre de WikiDex si la PokeAPI no lo trae en español, la PokeAPI tiene prioridad, una ruta con el nombre en inglés pasa al español y una renombrada con PUT no se toca | Que la PokeAPI traiga "Pikachu" y "Puño Trueno" tras reimportar |
| F7 Cadena evolutiva | Árbol desde la base, Eevee con 8 ramas ordenadas, igual desde cualquier etapa, etapa no importada con `null`, sin cadena → una etapa, 404 | Textos de `condicion` armados desde la PokeAPI |
| F8 Filtros combinados | Filtros vacíos ignorados, combinación con Y, con `sort`, generación/obtención inválidas → 400, tipo/región inexistentes → vacío, igual que el endpoint de un filtro | — |
| F15 Autenticación (B.30) | Login correcto (el token sirve para escribir), contraseña mala y usuario inexistente → 401 con el mismo mensaje, campos vacíos o sin cuerpo → 400, escribir sin token → 401 con el formato de error y `WWW-Authenticate`, token sin ADMIN → 403, token vencido → 401, firmado con otro secreto o mal formado → 401, GET públicos sin token, importación por GET sin token → 401, administrador inicial con BCrypt (se crea; no se toca si existe; no se crea sin datos), de punta a punta con PostgreSQL (login → POST /rutas); B.31: borrar una ruta importada la desvincula y la excluye, una creada a mano no se excluye, 404, sin sesión → 401, de punta a punta con PostgreSQL (Pikachu pierde el Bosque), y la reimportación no vuelve a crear un lugar excluido; B.32: editar guarda y marca `editadoAMano`, estadística en 300 o "abc", `ps` 0 y peso negativo → 400 con `campos`, vacíos, obtención y tipos desconocidos, más de 2 tipos y rutas inexistentes → 400 con su campo, 404, sin cuerpo → 400, crear (201, región según la generación, sale de los excluidos), número ocupado → 409, crear sin número, slug ni generación, borrar (204, excluido), 404, sin sesión → 401, la reimportación salta un excluido y conserva uno editado, `campos` en los 400 de movimientos y rutas, y de punta a punta con PostgreSQL (PUT con 2 tipos y rutas conservadas; DELETE de Cyndaquil quita sus filas de `pokemon_tipo` y `pokemon_ruta`) | Arrancar el servidor real sin `JWT_SECRETO` (no arranca) |
| F14 Dos tipos | `?tipo=fire&tipo=flying` = los que tienen ambos (SQL), orden de los tipos indiferente, repetidos y vacíos cuentan una vez, igual que `/tipo/{t}` con uno, combinado con región, generación, obtención y `sort` sin duplicados, 3 tipos → 400 | — |
| F9 Sin tildes | `psiquico`, `psíquico`, `puno` = `puño` (con `totalElements`), `pikachú`, `flabébé`, filtro combinado, extensión creada por `schema.sql` | Reiniciar el servidor real dos veces |
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
| Sin rastro (foto no guardada, log sin imagen) | Por diseño: el servicio solo lee los bytes en memoria y registra slug, confianza y tiempo. Sin prueba automática; revisar el log a mano |

Los criterios "solo a mano" se comprueban con `curl.exe` como dice cada spec (ver `CLAUDE.md`).
