# Decisiones · Pokedex+

Registro de decisiones técnicas y de producto. Si una decisión cambia, se agrega una fila nueva; no se borra la anterior.

| Fecha | Decisión | Por qué |
|---|---|---|
| 2026-11-11 | Spring Boot + PostgreSQL (en lugar de MySQL, que propone el diplomado) | Integridad referencial y restricciones estrictas (`NOT NULL`, claves foráneas) que evitan inconsistencias en las inserciones masivas desde la PokeAPI; buena integración con Spring Data JPA / Hibernate; extensiones útiles para el proyecto, como `unaccent` para buscar sin tildes (B.22) |
| 2026-11-11 | Los datos se importan desde la PokeAPI y se guardan en nuestra BD | El frontend no depende de la PokeAPI; las consultas y filtros son rápidos |
| 2026-11-14 | `idPokedex` es la clave primaria de `Pokemon` (sin un `id` aparte) | Es único y estable, y permite reimportar sin duplicar |
| 2026-11-14 | Las rutas son los lugares reales de `/pokemon/{id}/encounters` | Reemplazan las rutas de ejemplo inventadas por la primera versión |
| 2026-11-14 | `tipoObtencion` / `formaObtencion` calculados al importar; iniciales y fósiles en listas fijas | La PokeAPI no marca iniciales ni fósiles, y muchos Pokémon no tienen encuentros |
| 2026-11-14 | Credenciales en `application-local.properties`, fuera de git | No subir contraseñas al repositorio |
| 2026-11-14 | Timeouts de 5 s / 15 s en el cliente de la PokeAPI | Sin timeouts, una sola llamada colgada detenía la importación para siempre |
| 2026-11-14 | Solo una importación a la vez (409 si ya hay una en curso) | Dos importaciones simultáneas duplicaban trabajo y podían chocar |
| 2026-11-14 | El error 500 devuelve un mensaje genérico; el detalle va solo al log | No exponer SQL ni datos internos al cliente |
| 2026-11-14 | Carga por lotes (`default_batch_fetch_size`) en vez de `@EntityGraph` | `tipos` y `rutas` son `List`: traerlos juntos causa `MultipleBagFetchException` y paginación en memoria |
| 2026-11-14 | Formato fijo de paginación: `{ content, page }` (`via-dto`) | El formato de `PageImpl` puede cambiar entre versiones; el frontend necesita un contrato estable |
| 2026-11-14 | Los movimientos de un Pokémon se piden en un endpoint aparte | Son más de 100 por Pokémon; incluirlos haría pesados todos los listados |
| 2026-11-14 | Los endpoints de importación aceptan GET además de POST | Permite probarlos desde el navegador; es de uso interno, no del frontend |
| 2026-11-14 | Nombres en español en un campo nuevo `nombreEs`, sin tocar `nombre` | `nombre` (slug) es el identificador que usa el frontend y la búsqueda |
| 2026-11-14 | Al reimportar, los movimientos existentes solo actualizan `nombreEs` | No pisar `categoria` ni `potencia` editadas con PUT |
| 2026-11-14 | El MVP incluye las 9 generaciones (1025 Pokémon), no solo la generación 1 | El importador ya lo soporta; la Pokédex queda completa para el frontend |
| 2026-11-14 | Documentación con SDD en `docs/` | Requisito del diplomado; el agente trabaja a partir de los docs |
| 2026-11-14 | Las listas de movimientos y rutas se paginan igual que las de Pokémon, antes de empezar el frontend | Con las 9 generaciones son 797 movimientos y 620 rutas; cambiar el formato con el frontend ya escrito obligaría a reescribir esas pantallas |
| 2026-11-14 | `/pokemons/{id}/movimientos`, `/rutas/regiones` y `/tipos` siguen siendo arreglos sin paginar | Son listas acotadas (unos 100 movimientos por Pokémon, 9 regiones, 18 tipos) que la pantalla muestra completas |
| 2026-11-14 | Orden por defecto con `@SortDefault("nombre")`, no `@PageableDefault` | `@PageableDefault` fija su propio tamaño de página (10) e ignora el `default-page-size=20` de la configuración |
| 2026-11-14 | Un `sort` inválido en consultas `@Query` (`UnknownPathException` de Hibernate) también responde 400, solo si la petición trae `?sort=` | En esas consultas el error no es `PropertyReferenceException` y salía como 500; sin `?sort=` sería un error real de la consulta y debe seguir siendo 500 |
