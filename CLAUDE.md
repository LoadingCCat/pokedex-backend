# CLAUDE.md — Pokedex+ Backend

API REST de la Pokédex+ (proyecto universitario en equipo). Este repositorio es **solo el backend**; el frontend lo hace otra parte del equipo y consume esta API. El objetivo es dejar el backend lo más completo y sólido posible, sin romper el contrato que usa el frontend.

- Java 21 · Spring Boot 4.1.2 · Spring Data JPA / Hibernate 7 · PostgreSQL · Maven (wrapper) · Lombok
- Datos importados desde la PokeAPI (`https://pokeapi.co/api/v2`) con `RestTemplate`
- Documentación del contrato para el frontend: `README.md` (mantenerlo al día)

## Comandos (Windows, PowerShell, desde esta carpeta)

```powershell
.\mvnw.cmd clean compile          # compilar desde cero
.\mvnw.cmd spring-boot:run        # arrancar en http://localhost:8080
```

- Si al arrancar sale "Port 8080 was already in use", hay otro servidor corriendo: detenerlo antes de volver a arrancar.
- Llenar o actualizar la BD (tarda minutos; se puede repetir, no duplica):
  `POST http://localhost:8080/api/pokemons/importar-rango?total=151&movimientos=0` (todos: `total=1025`)
- Probar endpoints con `curl.exe "http://localhost:8080/api/..."`.
- Tests: solo existe `contextLoads()`, que necesita PostgreSQL encendido. No hay tests reales todavía.

## Configuración y credenciales

- Las credenciales de PostgreSQL van en `src/main/resources/application-local.properties`, que está en `.gitignore`. **Nunca** subirlo ni copiar su contenido a otro archivo. Plantilla: `application-local.properties.example`.
- `spring.jpa.hibernate.ddl-auto=update`: Hibernate agrega columnas y tablas nuevas, pero no borra ni renombra. Los campos nuevos deben aceptar `null` (los registros existentes quedan vacíos hasta reimportar). Renombrar o borrar columnas requiere hacerlo a mano en la BD.
- `spring.jpa.open-in-view=true` es necesario: las relaciones lazy (`tipos`, `rutas`) se cargan al convertir a JSON.
- `hibernate.default_batch_fetch_size=50` resuelve el N+1 de los listados. **No** usar `@EntityGraph` / `JOIN FETCH` sobre `tipos` y `rutas` a la vez: son `List` y causan `MultipleBagFetchException`, y con paginación Hibernate paginaría en memoria.

## Estructura

```
com.pokedex/
├── PokedexBackendApplication   # arranque + bean RestTemplate (timeouts 5 s conexión / 15 s lectura)
├── config/CorsConfig           # CORS abierto ("*") en /api/** para desarrollo
├── controller/                 # Pokemon, Movimiento, Ruta, Tipo — validan la entrada y lanzan excepciones propias
├── entity/                     # Pokemon, Movimiento, Ruta, Tipo
├── exception/                  # GlobalExceptionHandler + BadRequest / ResourceNotFound / Conflict
├── repository/                 # Spring Data JPA
└── service/PokemonService      # toda la importación desde la PokeAPI
```

## Modelo de datos (lo que no es obvio)

- **`Pokemon`**: la clave primaria es `idPokedex` (número oficial, no autoincremental; no hay otro `id`). Tiene `region` (de origen, según la generación), `tipoObtencion` + `formaObtencion`, `imagenUrl`, `altura` (m), `peso` (kg), las 6 estadísticas base (`ps`, `ataque`, `defensa`, `ataqueEspecial`, `defensaEspecial`, `velocidad`) y `getTotalEstadisticas()`, que se calcula y no se guarda.
- Relaciones `@ManyToMany` de `Pokemon`: `tipos` (tabla `pokemon_tipo`), `rutas` (`pokemon_ruta`) y `movimientos` (`pokemon_movimiento`). `movimientos` lleva `@JsonIgnore` porque son más de 100 por Pokémon; se exponen en `GET /api/pokemons/{id}/movimientos`.
- **`Ruta`** = lugar real de los juegos, sacado de `/pokemon/{id}/encounters`. `idPokeapi` (único) identifica las importadas; las creadas con `POST /rutas` lo tienen en `null` (el controller lo fuerza).
- **`Movimiento`**: `categoria` ∈ {Físico, Especial, Estado}; `esCompetitivo` lo calcula el servidor (`potencia > 80`). Antes de borrar uno hay que desvincularlo de `pokemon_movimiento` (`desvincularDePokemons`).
- `tipoObtencion` se decide en `definirObtencion()` en este orden: Inicial → Singular → Fósil → Legendario → Regalo → Salvaje → Huevo → Evolución → Sin datos. Iniciales y fósiles son listas fijas en `PokemonService` (la PokeAPI no los marca).
- `nombre` de Pokémon y movimientos es el slug en inglés de la PokeAPI (`thunder-punch`) y **no se cambia** (el frontend depende de él). `nombreEs` (nullable) es el nombre en español, sacado de `names` (idioma `es`, con respaldo en `en`). Las búsquedas por nombre miran ambos campos. Las rutas guardan directamente el nombre en español cuando existe. La descripción viene en español.

## Contrato de la API (el frontend depende de esto)

- Base: `http://localhost:8080/api`. Si cambias un endpoint, un campo o un formato, **actualiza el README** y avisa que el frontend se ve afectado.
- Listas de Pokémon paginadas con `?page=&size=&sort=` (por defecto 20, máximo 200). Formato fijo (`serialization-mode=via-dto`):
  `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`
- Todos los errores salen de `GlobalExceptionHandler` con el mismo JSON: `{ timestamp, status, error, mensaje, path }`.
  - 400 `BadRequestException`, parámetros/JSON inválidos, `sort` por un campo inexistente
  - 404 `ResourceNotFoundException`, URL inexistente
  - 405 método no permitido · 409 `ConflictException` (p. ej. importación ya en curso)
  - 500: mensaje genérico; el detalle solo va al log. No exponer `ex.getMessage()` en el 500.
- Los controllers nunca dejan escapar excepciones sin tratar: validan y lanzan las excepciones propias.

## Importación desde la PokeAPI (`PokemonService`)

- Por Pokémon: `/pokemon/{id}` (datos, tipos, estadísticas, movimientos), `/pokemon-species/{id}` (generación, descripción, legendario…) y `/pokemon/{id}/encounters` (rutas). `CacheImportacion` evita pedir dos veces la misma ruta o movimiento.
- Es idempotente: actualiza lo que existe y no duplica. Si falla una parte opcional (especie, rutas, movimientos), se registra en el log y se conserva lo que había.
- Solo una importación a la vez (`AtomicBoolean` → 409). Corre de forma síncrona dentro de la petición HTTP: con `total=1025` el cliente puede cortar antes de que termine, aunque el servidor siga.
- Los endpoints de importación aceptan GET y POST (GET para probar desde el navegador).

## Convenciones de código

- Nombres de clases, métodos, variables, comentarios y mensajes de error **en español**, como el resto del código.
- Inyección por constructor (sin `@Autowired` en campos). Entidades con Lombok `@Getter @Setter @NoArgsConstructor`.
- Consultas personalizadas: `@Query` explícita con `LOWER()` para comparar texto sin importar mayúsculas (PostgreSQL), o métodos derivados con `IgnoreCase`. Con `JOIN` + paginación, usar `DISTINCT` y un `countQuery` propio.
- El JSON de la PokeAPI se lee como `Map`/`List` sin tipar: usa los helpers `getJson`, `getJsonList`, `mapa`, `lista`, y `@SuppressWarnings("unchecked")` a nivel de método.
- Commits con mensajes cortos en español. Compilar (`.\mvnw.cmd clean compile`) y probar los endpoints afectados antes de hacer commit.

## Pendiente / ideas

- Búsqueda por nombre sin importar tildes (requiere la extensión `unaccent` de PostgreSQL).
- Cadena evolutiva: `GET /pokemons/{id}/evoluciones`.
- Filtros combinados (ej. `?tipo=fire&generacion=1`).
- Paginación en `/movimientos` y `/rutas`.
- Importación en segundo plano con endpoint de estado.
- Tests que no dependan de PostgreSQL (`@WebMvcTest`, o H2/Testcontainers).
- Al reimportar, de los movimientos ya existentes solo se actualiza `nombreEs` (no `categoria` ni `potencia`, para no pisar cambios hechos con PUT); los Pokémon sí se actualizan completos.
