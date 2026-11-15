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
| Controllers | `PokemonControllerTest`, `MovimientoControllerTest`, `RutaControllerTest` (`@WebMvcTest`) | Ninguna: repositorios y servicios simulados | Validaciones (400), 404, 405, 409, 202, formato `{ content, page }`, página de 20 / máximo 200, orden por defecto, formato de errores, 500 sin detalles |
| Servicios | `PokemonServiceTest`, `EvolucionServiceTest`, `ProgresoImportacionTest` | Ninguna: PokeAPI y repositorios simulados | Importación (datos, nombre en español, forma de obtención, timeouts), B.18, F10 (estados, 409, errores), árbol de evoluciones |
| API + SQL | `ApiConPostgresTest` (`@SpringBootTest`) | PostgreSQL 14 embebido (`PostgresEmbebido`) | Consultas reales: `unaccent`, `Specification` con `EXISTS`, región sin duplicados, `countQuery`, `sort` inválido en `@Query`, `schema.sql` y arranque de la aplicación |

No se usa H2: no tiene `unaccent` y obligaría a probar consultas distintas a las de PostgreSQL. No se usa Testcontainers porque necesita Docker.

## Criterios cubiertos

| Spec | Pruebas automáticas | Solo a mano (necesitan la PokeAPI real) |
|---|---|---|
| F1 Catálogo | Listado paginado y ordenado, máximo 200, ficha con `totalEstadisticas`, sin `movimientos`, 404, id no numérico, URL inexistente, 405, 500 | — |
| F2 Filtros | Generación 0/10 → 400, `obtencion` con o sin tilde e inválida, tipo sin mayúsculas, región con nativos + rutas sin duplicados, ruta inexistente → 404 | — |
| F3 Importación | 400 de `total`/`movimientos`, 409, timeout = fallido y sigue, datos y estadísticas, Inicial y Evolución, conserva rutas si `/encounters` falla | Importar `total=10` y `total=1025` de verdad (0 fallidos), repetir sin duplicar |
| F4 Movimientos | Listado por nombre, 404, búsqueda vacía → 400, crear (201, `esCompetitivo`, categoría normalizada, id ignorado), datos inválidos, repetido, PUT, DELETE desvincula, competitivos y categoría en SQL | — |
| F5 Rutas | Listado por nombre, regiones como arreglo, 404, POST fuerza `idPokeapi` a `null`, validaciones, repetida, PUT 404, rutas por región y Pokémon de una ruta en SQL | — |
| F6 Nombres en español | Búsqueda en `nombre` y `nombreEs` (SQL), `nombreEs` en blanco → `null`, PUT conserva `nombreEs`, reimportar solo cambia `nombreEs` (B.18) | Que la PokeAPI traiga "Pikachu" y "Puño Trueno" tras reimportar |
| F7 Cadena evolutiva | Árbol desde la base, Eevee con 8 ramas ordenadas, igual desde cualquier etapa, etapa no importada con `null`, sin cadena → una etapa, 404 | Textos de `condicion` armados desde la PokeAPI |
| F8 Filtros combinados | Filtros vacíos ignorados, combinación con Y, con `sort`, generación/obtención inválidas → 400, tipo/región inexistentes → vacío, igual que el endpoint de un filtro | — |
| F9 Sin tildes | `psiquico`, `psíquico`, `puno` = `puño` (con `totalElements`), `pikachú`, `flabébé`, filtro combinado, extensión creada por `schema.sql` | Reiniciar el servidor real dos veces |
| F10 Segundo plano | 202 al instante, 409 mientras corre, `terminada` con los 5 resultados, aceptar otra al terminar, `error` libera el bloqueo, ejecutor que rechaza la tarea, estados y porcentaje de `ProgresoImportacion` | La importación real de las 1025 en segundo plano |

Los criterios "solo a mano" se comprueban con `curl.exe` como dice cada spec (ver `CLAUDE.md`).
