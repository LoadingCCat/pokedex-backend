# Arquitectura · Pokedex+

## Visión general
```
Frontend (por definir, U3)  ──HTTP/JSON──▶  API REST Spring Boot  ──JPA──▶  PostgreSQL
                                                   │
                                                   └──HTTP (solo al importar)──▶  PokeAPI
```
El frontend solo habla con nuestra API. La PokeAPI se consulta únicamente durante la importación (F3); después, todo se sirve desde PostgreSQL.

## Backend (este repositorio)
- Java 21 · Spring Boot 4.1.2 · Spring Data JPA (Hibernate 7) · Maven (wrapper) · Lombok
- PostgreSQL (base de datos `pokedex_db`)
- `RestTemplate` con timeouts (5 s conexión, 15 s lectura) para la PokeAPI

### Capas (paquete `com.pokedex`)
| Paquete | Responsabilidad |
|---|---|
| `controller/` | Endpoints REST. Validan la entrada y lanzan excepciones propias |
| `service/` | `PokemonService`: toda la importación desde la PokeAPI |
| `repository/` | Acceso a datos con Spring Data JPA |
| `entity/` | Entidades JPA |
| `exception/` | `GlobalExceptionHandler` y excepciones propias (400, 404, 409) |
| `config/` | CORS |

## Datos
Entidades: **Pokemon**, **Tipo**, **Ruta**, **Movimiento**

```
Pokemon N──N Tipo         (pokemon_tipo)
Pokemon N──N Ruta         (pokemon_ruta)        lugares donde aparece en estado salvaje
Pokemon N──N Movimiento   (pokemon_movimiento)  movimientos que puede aprender
```

- `Pokemon.idPokedex` es la clave primaria: el número oficial de la Pokédex Nacional (1–1025).
- `Ruta.idPokeapi` identifica los lugares importados; es `null` en las rutas creadas a mano.
- El esquema lo mantiene Hibernate (`ddl-auto=update`): agrega columnas y tablas, pero no borra ni renombra.

## Contrato
`docs/contratos/api.md` es la fuente de verdad de endpoints, formatos y errores.

## Configuración
- Credenciales en `application-local.properties` (fuera de git) o en las variables `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.
- Carga por lotes de relaciones (`default_batch_fetch_size=50`) y `open-in-view=true`.

## Frontend (U3)
Por definir con el equipo. El stack del diplomado es Next.js + TypeScript + Tailwind. Consumirá `http://localhost:8080/api` en desarrollo.

## Despliegue (U6)
Por definir. El stack del diplomado propone AWS para el backend.
