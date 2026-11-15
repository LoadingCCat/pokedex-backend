# Bitácora de IA · Pokedex+ Backend

Registro de cómo se usó la IA: prompts clave, errores del agente y cómo se corrigieron.
Agregar una entrada por sesión de trabajo.

---

## Sesión anterior (chat aparte) · antes de 2026-11-14
**[completar]**: importación de rutas reales desde `/encounters`, forma de obtención, CRUD de movimientos y rutas, manejo de errores. Anotar aquí los prompts y los problemas de esa sesión.

---

## 2026-11-14 · Claude (chat con acceso a la carpeta del proyecto)

### Prompts clave
- *"Lee la estructura actual del proyecto… ¿Entendido? Dime qué necesitas que programemos o mejoremos."* → diagnóstico del backend con problemas priorizados (quedó en un Claude Doc).
- *"Sigamos el orden propuesto"* → 4 pasos: correcciones, push, rendimiento y paginación, y funcionalidades nuevas.
- *"Crea el CLAUDE.md"* → instrucciones del proyecto para Claude Code.
- *"Necesito hacer un SDD del proyecto… me faltaban features spec"* → la carpeta `docs/` con specs y criterios de aceptación.

### Lo que hizo el agente
| Cambio | Commit |
|---|---|
| Timeouts en la PokeAPI, 409 por importación en curso, `sort` inválido → 400, 500 sin detalles, `idPokeapi` protegido en `POST /rutas` | `d14b92e` |
| Carga por lotes contra el N+1 y formato fijo de paginación `{ content, page }` | `3d977a0` |
| Estadísticas base y `GET /pokemons/{id}/movimientos` | `5734778` |
| `CLAUDE.md` | `172b736` |
| Documentación SDD | (este commit) |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| Propuso `@EntityGraph` para el N+1, pero con dos `List` (`tipos`, `rutas`) Hibernate lanza `MultipleBagFetchException` y pagina en memoria | El mismo agente lo notó al implementar, antes de escribir código | Carga por lotes con `default_batch_fetch_size=50` |
| Al relacionar Pokémon con movimientos, `DELETE /movimientos/{id}` iba a fallar con 500 por la clave foránea | Revisión del diff antes de entregar | Desvincular de `pokemon_movimiento` antes de borrar |
| No podía compilar en su entorno (sin acceso a Maven Central) | Error 403 al compilar | El usuario compiló en su PC (`.\mvnw.cmd clean compile`); por eso se recomienda seguir en Claude Code |
| Dio comandos asumiendo la carpeta correcta; el usuario estaba en `pokedex+` y no en `pokedex-backend` | Error "mvnw.cmd no se reconoce" / "not a git repository" | Indicar el `cd` y abrir VS Code directamente en `pokedex-backend` |
| Dio varios comandos de CMD juntos, con comentarios `#`; al pegarlos se unieron (`git statusgit add .`) | Salida de la terminal | Dar los comandos de uno en uno y sin comentarios |

### Otros hallazgos
- El SDD inicial estaba desactualizado: describía un `id` autoincremental en `Pokemon`, pocos endpoints y CORS solo para `localhost:8080`.
- Había mucho trabajo local sin subir a GitHub (el remoto seguía en la versión del 11/nov).
- Los errores falsos de VS Code ("java.lang.Object cannot be resolved") venían de la extensión de Java, no del código: Maven compilaba bien.

### Verificación
Pikachu (`/api/pokemons/25`) con `totalEstadisticas = 320` y unos 110 movimientos en `/api/pokemons/25/movimientos`. Se revisó desde el navegador del usuario.

---

## 2026-11-14 · Claude Code (en la carpeta del proyecto)

### Prompt clave
*"Lee CLAUDE.md y README.md… Tarea: agregar nombres en español a Pokémon y movimientos usando la PokeAPI. No cambies el campo "nombre"… Antes de escribir código, muéstrame un plan corto y espera mi aprobación…"* (con requisitos, pasos de verificación y "no hagas push sin preguntarme").

### Lo que hizo el agente
| Cambio | Commit |
|---|---|
| F6 · `nombreEs` en Pokemon y Movimiento, búsqueda en ambos campos, reimportación de `nombreEs` en movimientos existentes, README y CLAUDE.md actualizados | `dbcfb78` |

| B.17 · Paginación en `/movimientos` y `/rutas`; `sort` inválido → 400 también en consultas `@Query`; B.23 verificada (1025 importados, 0 fallidos) | `68f8aa1` |
| B.16 · F8 filtros combinados en `GET /pokemons` (`PokemonFiltros` con `Specification`) | `af3898a` |
| B.24 · Orden por defecto `idPokedex` en las listas de Pokémon (hallado al probar F8: el catálogo empezaba en pikachu) | `ab7a2d4` |
| B.15 · F7 cadena evolutiva: tabla `evolucion` llenada al importar y `GET /pokemons/{id}/evoluciones` | (este commit) |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| El plan de F6 pedía verificar con `total=10`, pero Pikachu (25) queda fuera de ese rango | El agente lo avisó en el plan y lo confirmó al probar (`nombreEs` vacío) | Reimportar con `total=25` para la verificación |
| Regresión de F6 (`dbcfb78`): al pasar `/pokemons/buscar` de método derivado a `@Query`, `?sort=foo` empezó a responder 500 en vez de 400 (Hibernate lanza `UnknownPathException`, no `PropertyReferenceException`). En F6 no se probó el `sort` inválido | Al probar con `curl` los endpoints paginados de B.17 | `GlobalExceptionHandler` trata `UnknownPathException` como 400 cuando la petición trae `?sort=`. También corrigió `/pokemons/region` y `/pokemons/ruta`, que tenían el fallo desde antes |
| Para el orden por defecto iba a usar `@PageableDefault`, que fija el tamaño de página en 10 e ignora el 20 configurado | Revisión antes de escribir el código | `@SortDefault("nombre")` |
| En F7, la primera versión mezclaba las evoluciones de formas regionales con la normal (Slowbro: "Nivel 37 o Usando Brazal Galanuez") y le faltaban 3 métodos de evolución de la PokeAPI | Revisión con el advisor antes de cerrar la tarea, mirando la salida de las pruebas | Se usan `required_pokemon_form` / `evolved_pokemon_form` para descartar o aclarar las vías regionales, y se contrastó la lista con `/evolution-trigger` |

**[completar]**: otros problemas que hayas notado tú durante la sesión.

---

## 2026-11-14/15 · Claude Code (PC nueva)

### Prompts clave
- *"Acabo de pasar este proyecto a una PC nueva… ayúdame a dejarlo al día"* → revisar git contra `origin/main`, mover la skill a `.claude/skills/`, comprobar que `application-local.properties` no se sube, importar las 1025 y verificar B.25.
- *"Quiero dejar el backend completo antes de empezar el frontend. Trabaja UNA tarea a la vez… muéstrame un plan corto, espera mi aprobación…"* → 6 tareas en orden: docs al día, B.25, B.18 (decisión del usuario), B.22, B.20 y B.19 (con el enfoque aprobado antes).

### Lo que hizo el agente
| Cambio | Commit |
|---|---|
| Skill `nuevo-endpoint` movida a `.claude/skills/nuevo-endpoint/SKILL.md`; plantilla `application-local.properties.example` restaurada (se había perdido al copiar el proyecto) | `4db88cc` |
| Docs: criterios F1–F8 en `producto.md` y en B.19 (el resto ya estaba al día) | `945ed46` |
| B.25 confirmada sin cambios (1025 importados, 0 fallidos, Eevee con 8 ramas) | — |
| B.18 · Decisión del usuario: se deja como está (al reimportar, los movimientos solo actualizan `nombreEs`) | `29372b2` |
| B.22 · F9 búsqueda sin tildes con `unaccent` (`schema.sql` crea la extensión al arrancar) | `127a14c` |
| B.20 · F10 importación en segundo plano (202) con `GET /api/pokemons/importacion`; reimportación de las 1025 verificada (0 fallidos, 9,5 min) | `25e7f10` |
| B.19 · 80 pruebas automáticas de F1–F10: `@WebMvcTest`, servicios con simulaciones y `@SpringBootTest` contra PostgreSQL embebido (zonky); `docs/pruebas.md` | `33fc0ee` |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| Dijo que el servidor no mostraba en el log el avance de la importación; sí lo muestra cada 25 Pokémon | Al cerrar la terminal del servidor se vieron las líneas "Importando Pokémon... 850/1025" | Se corrigió en el plan de B.20 y ahora el avance también está en `GET /api/pokemons/importacion` |
| Arrancó el servidor desde su Bash: Tomcat falló con "Unable to establish loopback connection" (el Bash del agente no permite abrir puertos locales). Lo mismo pasaría con el PostgreSQL embebido de las pruebas | Al leer el log del arranque fallido | El servidor y las pruebas se lanzan en la terminal de la app; quedó anotado en `CLAUDE.md` y `docs/pruebas.md` |
| El plan aprobado de B.20 proponía una transacción por Pokémon (`TransactionTemplate`). Al implementarlo vio que, si una se deshacía, la caché de la importación quedaba con rutas y movimientos inexistentes y los Pokémon siguientes fallarían en cadena | Revisión del código antes de compilar | Se cambió la única lectura lazy (`pokemon.getRutas()`) por una consulta (`RutaRepository.buscarPorPokemon`); se avisó del cambio de plan y se registró en `decisiones.md` |
| El 202 de la importación salía con `fase: null` y `total: 0` (el hilo de fondo aún no había empezado) | Al probar con `curl` | `ProgresoImportacion.iniciar(total)` deja la fase `pokemons` y el total desde el principio |
| 7 de las 80 pruebas fallaron la primera vez: `jsonPath("$.content[*]…").value(List.of(…))` no funciona en Spring 7 para rutas con `[*]` (no era un error de la API) | Al correr `mvnw test` | Se usa el matcher `contains(…)` de Hamcrest |
| Al editar `CLAUDE.md` con `sed`, se perdió la barra invertida de `.\mvnw.cmd` | Revisión del resultado | Corregido con una edición exacta |

**[completar]**: otros problemas que hayas notado tú durante la sesión (por ejemplo, la lentitud de la PC que obligó a reiniciar a mitad de la primera importación).
