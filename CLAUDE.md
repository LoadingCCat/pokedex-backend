# CLAUDE.md — Pokedex+ Backend

API REST de la Pokédex+ (proyecto universitario en equipo). Este repositorio es **solo el backend**; el frontend lo hace otra parte del equipo y consume esta API. El objetivo es dejar el backend lo más completo y sólido posible, sin romper el contrato que usa el frontend.

- Java 21 · Spring Boot 4.1.2 · Spring Data JPA / Hibernate 7 · PostgreSQL · Maven (wrapper) · Lombok
- Datos importados desde la PokeAPI (`https://pokeapi.co/api/v2`) con `RestTemplate`

## Documentación (léela antes de cualquier cambio)

El proyecto sigue Spec-Driven Development (SDD): primero el documento, después el código.

- Qué es el producto y qué NO entra: @docs/producto.md
- Stack, capas y modelo de datos: @docs/arquitectura.md
- Contrato de la API para el frontend: @docs/contratos/api.md
- Funcionalidades y criterios de aceptación: `docs/specs/` (una spec por funcionalidad, F1…)
- Qué prueba automática cubre cada criterio: `docs/pruebas.md`
- Por qué se decidió cada cosa: `docs/decisiones.md`
- Qué está hecho y qué sigue: `docs/tareas.md`
- Prompts y errores del agente: `BITACORA_IA.md`
- Procedimiento para agregar o cambiar endpoints: skill `nuevo-endpoint` (`.claude/skills/`)

## Reglas de trabajo

- Implementa **una** tarea de `docs/tareas.md` a la vez y márcala `[x]` al terminar.
- No agregues funcionalidades que no tengan spec en `docs/specs/`. Si el alcance cambia, propón primero el cambio en los docs y espera aprobación.
- Antes de un cambio importante, muestra un plan y espera aprobación.
- No toques archivos que la tarea no requiera.
- Al terminar: compila, prueba con `curl.exe` los criterios de aceptación de la spec, actualiza `docs/contratos/api.md` si cambió el contrato, y explica cómo probarlo.
- Registra en `docs/decisiones.md` las decisiones técnicas y en `BITACORA_IA.md` los errores importantes del agente.
- No hagas `git push` sin preguntar.

## Comandos (Windows, PowerShell, desde esta carpeta)

```powershell
.\mvnw.cmd clean compile          # compilar desde cero
.\mvnw.cmd spring-boot:run        # arrancar en http://localhost:8080
```

- Si al arrancar sale "Port 8080 was already in use", hay otro servidor corriendo: detenerlo antes de volver a arrancar.
- Llenar o actualizar la BD (tarda minutos; se puede repetir, no duplica):
  `POST http://localhost:8080/api/pokemons/importar-rango?total=151&movimientos=0` (todos: `total=1025`, unos 20 min). Responde 202 al instante; el avance, en `GET http://localhost:8080/api/pokemons/importacion`
- Probar endpoints con `curl.exe "http://localhost:8080/api/..."`.
- Tests: `.\mvnw.cmd test` (107 pruebas, F1–F15). Los `@WebMvcTest` llevan `@ConSeguridad` (reglas reales + secreto de prueba) y, los de escritura, `@WithMockUser(roles = "ADMIN")`. No necesitan el PostgreSQL local ni la PokeAPI: los controllers y servicios usan simulaciones, y las consultas SQL corren contra un PostgreSQL embebido (zonky). Se lanzan desde la terminal de la app, no desde el Bash del agente. Detalle en `docs/pruebas.md`.

## Configuración y credenciales

- Las credenciales de PostgreSQL van en `src/main/resources/application-local.properties`, que está en `.gitignore`. **Nunca** leerlo, mostrarlo, subirlo ni copiar su contenido a otro archivo (tampoco con un `grep` sobre `src/`: hay que excluirlo). Plantilla: `application-local.properties.example`.
- F15: en ese mismo archivo (o como variables de entorno) van `JWT_SECRETO` (al menos 32 caracteres; **sin él el servidor no arranca**), `ADMIN_USERNAME` y `ADMIN_PASSWORD` (el administrador se crea al arrancar si no existe) y, opcional, `CORS_ORIGENES`.
- `spring.jpa.hibernate.ddl-auto=update`: Hibernate agrega columnas y tablas nuevas, pero no borra ni renombra. Los campos nuevos deben aceptar `null` (los registros existentes quedan vacíos hasta reimportar). Renombrar o borrar columnas requiere hacerlo a mano en la BD.
- `spring.jpa.open-in-view=true` es necesario: las relaciones lazy (`tipos`, `rutas`) se cargan al convertir a JSON.
- `hibernate.default_batch_fetch_size=50` resuelve el N+1 de los listados. **No** usar `@EntityGraph` / `JOIN FETCH` sobre `tipos` y `rutas` a la vez: son `List` y causan `MultipleBagFetchException`, y con paginación Hibernate paginaría en memoria.

## Estructura

```
com.pokedex/
├── PokedexBackendApplication   # arranque + bean RestTemplate (timeouts 5 s conexión / 15 s lectura)
├── config/SeguridadConfig      # F15: Spring Security sin sesión, JWT HS256 (oauth2-resource-server), reglas
│                               #   (GET públicos; escrituras e importación, ADMIN), BCrypt y CORS (CORS_ORIGENES)
├── config/ErroresSeguridad     # 401/403 con el mismo JSON de error que GlobalExceptionHandler
├── controller/                 # Pokemon, Movimiento, Ruta, Tipo, Auth (login) — validan la entrada y lanzan excepciones propias
├── entity/                     # Pokemon, Movimiento, Ruta, Tipo, Evolucion, Usuario (BCrypt)
├── exception/                  # GlobalExceptionHandler + BadRequest / ResourceNotFound / Conflict
├── repository/                 # Spring Data JPA
└── service/                    # PokemonService: toda la importación desde la PokeAPI
                                # EvolucionService: arma el árbol de la cadena evolutiva desde la BD
                                # ServicioToken (JWT del login) y AdministradorInicial (crea el ADMIN al arrancar)
```

## Modelo de datos (lo que no es obvio)

- **`Pokemon`**: la clave primaria es `idPokedex` (número oficial, no autoincremental; no hay otro `id`). Tiene `region` (de origen, según la generación), `tipoObtencion` + `formaObtencion`, `imagenUrl`, `altura` (m), `peso` (kg), las 6 estadísticas base (`ps`, `ataque`, `defensa`, `ataqueEspecial`, `defensaEspecial`, `velocidad`) y `getTotalEstadisticas()`, que se calcula y no se guarda.
- Relaciones `@ManyToMany` de `Pokemon`: `tipos` (tabla `pokemon_tipo`), `rutas` (`pokemon_ruta`) y `movimientos` (`pokemon_movimiento`). `movimientos` lleva `@JsonIgnore` porque son más de 100 por Pokémon; se exponen en `GET /api/pokemons/{id}/movimientos`.
- **`Ruta`** = lugar real de los juegos, sacado de `/pokemon/{id}/encounters`. `idPokeapi` (único) identifica las importadas; las creadas con `POST /rutas` lo tienen en `null` (el controller lo fuerza).
- **`Movimiento`**: `categoria` ∈ {Físico, Especial, Estado}; `tipo` (nullable) con el nombre en inglés de los tipos (`electric`), validado contra los 18 en POST/PUT; `esCompetitivo` lo calcula el servidor (`potencia > 80`). Antes de borrar uno hay que desvincularlo de `pokemon_movimiento` (`desvincularDePokemons`).
- **`Evolucion`** (tabla `evolucion`): una fila por etapa de cada cadena (`idPokedex`, `idCadena`, `idPreEvolucion`, `condicion` en español ya armada). Sin clave foránea a `Pokemon`, porque una cadena puede incluir Pokémon no importados. Se llena al importar (`importarCadenaEvolutiva`, una vez por cadena) y se expone como árbol en `GET /api/pokemons/{id}/evoluciones`.
- `tipoObtencion` se decide en `definirObtencion()` en este orden: Inicial → Singular → Fósil → Legendario → Regalo → Salvaje → Huevo → Evolución → Sin datos. Iniciales y fósiles son listas fijas en `PokemonService` (la PokeAPI no los marca).
- `nombre` de Pokémon y movimientos es el slug en inglés de la PokeAPI (`thunder-punch`) y **no se cambia** (el frontend depende de él). `nombreEs` (nullable) es el nombre en español, sacado de `names` (idioma `es`, con respaldo en `en`). Las búsquedas por nombre miran ambos campos. Las rutas guardan directamente el nombre en español cuando existe. La descripción viene en español.

## Contrato de la API (el frontend depende de esto)

- Base: `http://localhost:8080/api`. Si cambias un endpoint, un campo o un formato, **actualiza `docs/contratos/api.md`** en el mismo commit y avisa que el frontend se ve afectado. El README solo tiene la instalación y el índice de docs.
- Listas de Pokémon, movimientos y rutas paginadas con `?page=&size=&sort=` (por defecto 20, máximo 200; sin `sort`, los Pokémon se ordenan por `idPokedex` y movimientos y rutas por `nombre`, con `@SortDefault`, no `@PageableDefault`, que cambia el tamaño a 10). Siguen como arreglo: `/pokemons/{id}/movimientos`, `/rutas/regiones`, `/tipos`. Formato fijo (`serialization-mode=via-dto`):
  `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`
- `GET /pokemons` acepta filtros opcionales combinados con Y (`nombre`, `generacion`, `tipo`, `region`, `obtencion`), armados en `repository/PokemonFiltros` (`Specification` con `EXISTS`, sin `JOIN`). Un filtro nuevo se agrega ahí, no como otra `@Query`.
- Seguridad (F15): todos los `GET` son públicos salvo la importación; `POST`, `PUT`, `DELETE` y la importación piden `Authorization: Bearer <token>` con rol ADMIN (token de `POST /api/auth/login`). Las reglas están en `SeguridadConfig`: un endpoint de escritura nuevo queda protegido sin hacer nada.
- Todos los errores salen de `GlobalExceptionHandler` (y los 401/403 de los filtros, de `ErroresSeguridad`) con el mismo JSON: `{ timestamp, status, error, mensaje, path }`.
  - 400 `BadRequestException`, parámetros/JSON inválidos, `sort` por un campo inexistente
  - 404 `ResourceNotFoundException`, URL inexistente
  - 401 sin token o con uno inválido, o `NoAutorizadoException` (login) · 403 sin el rol ADMIN
  - 405 método no permitido · 409 `ConflictException` (p. ej. importación ya en curso)
  - 500: mensaje genérico; el detalle solo va al log. No exponer `ex.getMessage()` en el 500.
- Los controllers nunca dejan escapar excepciones sin tratar: validan y lanzan las excepciones propias.

## Importación desde la PokeAPI (`PokemonService`)

- Por Pokémon: `/pokemon/{id}` (datos, tipos, estadísticas, movimientos), `/pokemon-species/{id}` (generación, descripción, legendario…) y `/pokemon/{id}/encounters` (rutas). `CacheImportacion` evita pedir dos veces la misma ruta o movimiento.
- Es idempotente: actualiza lo que existe y no duplica. Si falla una parte opcional (especie, rutas, movimientos), se registra en el log y se conserva lo que había.
- Solo una importación a la vez (`AtomicBoolean` → 409, tomado en el hilo de la petición). Corre en segundo plano (F10, `applicationTaskExecutor`): `importar-rango` responde 202 al instante y el avance se consulta en `GET /api/pokemons/importacion` (progreso en memoria, en `ProgresoImportacion`).
- En el hilo de fondo no hay sesión de BD (`open-in-view` solo aplica a peticiones): la importación **no** puede leer relaciones lazy (`pokemon.getRutas()`…); usar una consulta del repositorio.
- El servidor se arranca en la terminal de la app, no desde el Bash del agente: ahí Tomcat no puede abrir el puerto ("Unable to establish loopback connection").
- Los endpoints de importación aceptan GET y POST (GET para probar desde el navegador).

## Convenciones de código

- Nombres de clases, métodos, variables, comentarios y mensajes de error **en español**, como el resto del código.
- Inyección por constructor (sin `@Autowired` en campos). Entidades con Lombok `@Getter @Setter @NoArgsConstructor`.
- Consultas personalizadas: `@Query` explícita con `LOWER()` para comparar texto sin importar mayúsculas (PostgreSQL), o métodos derivados con `IgnoreCase`. Con `JOIN` + paginación, usar `DISTINCT` y un `countQuery` propio. Para ignorar tildes, `function('unaccent', …)` en JPQL o `cb.function("unaccent", …)` en una `Specification` (F9); la extensión la crea `schema.sql` al arrancar.
- El JSON de la PokeAPI se lee como `Map`/`List` sin tipar: usa los helpers `getJson`, `getJsonList`, `mapa`, `lista`, y `@SuppressWarnings("unchecked")` a nivel de método.
- Commits con mensajes cortos en español. Compilar (`.\mvnw.cmd clean compile`) y probar los endpoints afectados antes de hacer commit.

## Pendiente

La lista de pendientes vive en `docs/tareas.md` (sección "Back · pendiente"). Las specs F1–F10 de `docs/specs/` están todas implementadas.

## Notas

- Al reimportar, de los movimientos ya existentes solo se actualiza `nombreEs`, y `tipo` si estaba en `null` (no `categoria` ni `potencia`, para no pisar cambios hechos con PUT); los Pokémon sí se actualizan completos.
