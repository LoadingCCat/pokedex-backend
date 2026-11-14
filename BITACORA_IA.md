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
| B.16 · F8 filtros combinados en `GET /pokemons` (`PokemonFiltros` con `Specification`) | (este commit) |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| El plan de F6 pedía verificar con `total=10`, pero Pikachu (25) queda fuera de ese rango | El agente lo avisó en el plan y lo confirmó al probar (`nombreEs` vacío) | Reimportar con `total=25` para la verificación |
| Regresión de F6 (`dbcfb78`): al pasar `/pokemons/buscar` de método derivado a `@Query`, `?sort=foo` empezó a responder 500 en vez de 400 (Hibernate lanza `UnknownPathException`, no `PropertyReferenceException`). En F6 no se probó el `sort` inválido | Al probar con `curl` los endpoints paginados de B.17 | `GlobalExceptionHandler` trata `UnknownPathException` como 400 cuando la petición trae `?sort=`. También corrigió `/pokemons/region` y `/pokemons/ruta`, que tenían el fallo desde antes |
| Para el orden por defecto iba a usar `@PageableDefault`, que fija el tamaño de página en 10 e ignora el 20 configurado | Revisión antes de escribir el código | `@SortDefault("nombre")` |

**[completar]**: otros problemas que hayas notado tú durante la sesión.
