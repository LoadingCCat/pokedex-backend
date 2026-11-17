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

**Seguridad en las pruebas (desde B.30):** los `@WebMvcTest` cargan las reglas reales con `@ConSeguridad`, que importa `SeguridadConfig` y fija un secreto de JWT que solo existe en las pruebas. Las pruebas de F1–F14 que escriben (POST, PUT, DELETE, importar) corren con `@WithMockUser(roles = "ADMIN")` a nivel de clase: es el único cambio en ellas, y siguen comprobando lo mismo. Lo que pasa sin sesión o sin rol se prueba aparte (`SeguridadControllerTest` y los casos `@WithAnonymousUser`). `ApiConPostgresTest` define su propio administrador de prueba.

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
| F15 Autenticación (B.30) | Login correcto (el token sirve para escribir), contraseña mala y usuario inexistente → 401 con el mismo mensaje, campos vacíos o sin cuerpo → 400, escribir sin token → 401 con el formato de error y `WWW-Authenticate`, token sin ADMIN → 403, token vencido → 401, firmado con otro secreto o mal formado → 401, GET públicos sin token, importación por GET sin token → 401, administrador inicial con BCrypt (se crea; no se toca si existe; no se crea sin datos), de punta a punta con PostgreSQL (login → POST /rutas) | Arrancar el servidor real sin `JWT_SECRETO` (no arranca) |
| F14 Dos tipos | `?tipo=fire&tipo=flying` = los que tienen ambos (SQL), orden de los tipos indiferente, repetidos y vacíos cuentan una vez, igual que `/tipo/{t}` con uno, combinado con región, generación, obtención y `sort` sin duplicados, 3 tipos → 400 | — |
| F9 Sin tildes | `psiquico`, `psíquico`, `puno` = `puño` (con `totalElements`), `pikachú`, `flabébé`, filtro combinado, extensión creada por `schema.sql` | Reiniciar el servidor real dos veces |
| F10 Segundo plano | 202 al instante, 409 mientras corre, `terminada` con los 5 resultados, aceptar otra al terminar, `error` libera el bloqueo, ejecutor que rechaza la tarea, estados y porcentaje de `ProgresoImportacion` | La importación real de las 1025 en segundo plano |

Los criterios "solo a mano" se comprueban con `curl.exe` como dice cada spec (ver `CLAUDE.md`).
