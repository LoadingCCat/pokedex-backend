# Pokedex+ — Backend

API REST de la Pokedex+ hecha con Java 21, Spring Boot 4.1.2, Maven y PostgreSQL. Los datos se importan desde la [PokeAPI](https://pokeapi.co/).

## Cómo correrlo

1. Crea la base de datos `pokedex_db` en PostgreSQL.
2. Copia `src/main/resources/application-local.properties.example` como `application-local.properties` en la misma carpeta y pon tu usuario y contraseña. Ese archivo no se sube a git.
3. Ejecuta:
   ```
   .\mvnw.cmd spring-boot:run
   ```
4. Llena la base de datos (tarda unos minutos porque consulta la PokeAPI):
   ```
   http://localhost:8080/api/pokemons/importar-rango?total=151&movimientos=100
   ```
   Para todos los Pokémon usa `total=1025`. Se puede ejecutar varias veces: actualiza lo que ya existe y no duplica nada.
   La primera vez tarda más (unos minutos para 151) porque también descarga los lugares donde aparece cada Pokémon.

## Endpoints

Base URL: `http://localhost:8080/api`

### Pokémon — `/pokemons`
Todas las listas son paginadas: `?page=0&size=20&sort=idPokedex,asc` (máximo 200 por página).

Formato de una respuesta paginada (siempre igual):
```json
{
  "content": [ { "idPokedex": 1, "nombre": "bulbasaur", "...": "..." } ],
  "page": { "size": 20, "number": 0, "totalElements": 151, "totalPages": 8 }
}
```
`page.number` empieza en 0. Un `sort` por un campo que no existe responde 400.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/pokemons` | Listado paginado |
| GET | `/pokemons/buscar?nombre=pika` | Búsqueda por nombre (parcial, sin importar mayúsculas) |
| GET | `/pokemons/generacion/{1-9}` | Filtrar por generación |
| GET | `/pokemons/tipo/{nombre}` | Filtrar por tipo (`fire`, `water`, …) |
| GET | `/pokemons/region/{nombre}` | Pokémon nativos de la región **o** que aparecen en alguna de sus rutas (`kanto`, `johto`, `teselia`, …) |
| GET | `/pokemons/ruta/{idRuta}` | Pokémon que aparecen en una ruta |
| GET | `/pokemons/obtencion/{tipo}` | Filtrar por forma de obtención (`inicial`, `legendario`, `fosil`, …; con o sin tilde) |
| GET | `/pokemons/{id}` | Detalle (404 si no existe) |
| GET/POST | `/pokemons/importar-rango?total=N&movimientos=M` | Importar desde PokeAPI |
| GET/POST | `/pokemons/importar-todo` | Importación rápida (50 + 50) |

Ejemplo de Pokémon:
```json
{
  "idPokedex": 25,
  "nombre": "pikachu",
  "descripcion": "Cuando se enfada, descarga al instante la energía...",
  "generacion": 1,
  "region": "Kanto",
  "tipoObtencion": "Inicial",
  "formaObtencion": "Pokémon inicial: se elige al comenzar la aventura en la región de Kanto. También aparece en 12 lugares de Kanto, Johto y Hoenn.",
  "imagenUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/.../25.png",
  "altura": 0.4,
  "peso": 6.0,
  "tipos": [{ "id": 1, "nombre": "electric" }],
  "rutas": [{ "idRuta": 3, "nombre": "Bosque Verde", "region": "Kanto", "descripcion": "...", "idPokeapi": 321 }]
}
```

### Movimientos — `/movimientos`
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/movimientos` | Listado completo |
| GET | `/movimientos/{id}` | Detalle (404 si no existe) |
| GET | `/movimientos/buscar?nombre=...` | Búsqueda por nombre |
| GET | `/movimientos/categoria/{Físico\|Especial\|Estado}` | Filtrar por categoría |
| GET | `/movimientos/competitivos` | Movimientos con potencia > 80 |
| POST | `/movimientos` | Crear (`nombre`, `categoria`, `potencia`) |
| PUT | `/movimientos/{id}` | Actualizar |
| DELETE | `/movimientos/{id}` | Eliminar |

`esCompetitivo` lo calcula el servidor: es `true` si la potencia es mayor a 80.

### Rutas — `/rutas`
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/rutas` | Listado completo |
| GET | `/rutas/regiones` | Nombres de las regiones de origen (Kanto … Paldea) |
| GET | `/rutas/region/{nombre}` | Rutas de una región |
| GET | `/rutas/{id}` | Detalle (404 si no existe) |
| POST | `/rutas` | Crear (`nombre`, `region`, `descripcion`) |
| PUT | `/rutas/{id}` | Actualizar |

### Tipos — `/tipos`
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/tipos` | Todos los tipos guardados |

## Errores

Todos los errores devuelven el mismo formato:
```json
{
  "timestamp": "2026-11-14T10:05:28.207147",
  "status": 404,
  "error": "Recurso no encontrado",
  "mensaje": "Pokémon con id 9999 no existe",
  "path": "/api/pokemons/9999"
}
```

| Código | Cuándo |
|---|---|
| 400 | Parámetro inválido o faltante, JSON mal formado, datos inválidos en POST/PUT, `sort` por un campo que no existe |
| 404 | La URL no existe o el ID no existe |
| 405 | Método HTTP no permitido en esa ruta |
| 409 | Se pidió una importación mientras otra sigue en curso |
| 500 | Error inesperado del servidor (el detalle solo queda en el log, no en la respuesta) |

## Notas

- `region` del Pokémon es su **región de origen** (según su generación).
- `rutas` son los **lugares reales** de los juegos donde aparece en estado salvaje, sacados de `/pokemon/{id}/encounters` de la PokeAPI. Incluye lugares de todas las regiones (por ejemplo, Pikachu aparece en Kanto y también en otras regiones).
- Algunos Pokémon no tienen rutas porque la PokeAPI no tiene encuentros para ellos: iniciales, legendarios de evento (como Mew) y casi todos los de Galar y Paldea. Para esos casos están `tipoObtencion` y `formaObtencion`, que explican cómo se consigue cada Pokémon.

### Formas de obtención (`tipoObtencion`)

Se revisan en este orden y se usa la primera que aplique:

| Valor | Cuándo |
|---|---|
| Inicial | Se elige al comenzar el juego (incluye Pikachu y Eevee) |
| Singular | Pokémon mítico (Mew, Celebi…): solo por eventos |
| Fósil | Se revive a partir de un fósil (o evoluciona de uno) |
| Legendario | Se encuentra una sola vez en la historia |
| Regalo | Solo se recibe como regalo de un personaje |
| Salvaje | Se captura en rutas; `formaObtencion` dice dónde y cómo (hierba alta, surf, cañas…) |
| Huevo | Pokémon bebé, se obtiene de un huevo |
| Evolución | No aparece salvaje; se obtiene evolucionando a su preevolución |
| Sin datos | La PokeAPI no tiene información (sobre todo Galar y Paldea) |

Legendario, Singular, Huevo, Evolución y los métodos de encuentro salen de la PokeAPI. Los iniciales y los fósiles están en una lista fija en `PokemonService`, porque la PokeAPI no los marca.
- Las rutas importadas tienen `idPokeapi`; las creadas a mano con `POST /rutas` lo tienen en `null`.
- La descripción viene de la Pokédex en español; si no existe, se usa la de inglés.
