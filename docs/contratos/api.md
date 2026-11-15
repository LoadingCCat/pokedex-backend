# Contrato de la API · Pokedex+

Fuente de verdad de lo que el frontend puede usar. **Si cambias un endpoint, un campo o un formato, actualiza este archivo en el mismo commit** y avisa al equipo de frontend.

Base URL: `http://localhost:8080/api`

## Pokémon — `/pokemons`
Todas las listas son paginadas: `?page=0&size=20&sort=idPokedex,asc` (máximo 200 por página).

Formato de una respuesta paginada (siempre igual):
```json
{
  "content": [ { "idPokedex": 1, "nombre": "bulbasaur", "nombreEs": "Bulbasaur", "...": "..." } ],
  "page": { "size": 20, "number": 0, "totalElements": 151, "totalPages": 8 }
}
```
`page.number` empieza en 0. Si no se envía `sort`, las listas de Pokémon se ordenan por `idPokedex` ascendente. Un `sort` por un campo que no existe responde 400.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/pokemons` | Listado paginado, con filtros opcionales combinables (ver abajo) |
| GET | `/pokemons/buscar?nombre=pika` | Búsqueda por nombre (parcial, sin importar mayúsculas); busca en `nombre` y en `nombreEs` |
| GET | `/pokemons/generacion/{1-9}` | Filtrar por generación |
| GET | `/pokemons/tipo/{nombre}` | Filtrar por tipo (`fire`, `water`, …) |
| GET | `/pokemons/region/{nombre}` | Pokémon nativos de la región **o** que aparecen en alguna de sus rutas (`kanto`, `johto`, `teselia`, …) |
| GET | `/pokemons/ruta/{idRuta}` | Pokémon que aparecen en una ruta |
| GET | `/pokemons/obtencion/{tipo}` | Filtrar por forma de obtención (`inicial`, `legendario`, `fosil`, …; con o sin tilde) |
| GET | `/pokemons/{id}` | Detalle (404 si no existe) |
| GET | `/pokemons/{id}/movimientos` | Movimientos que puede aprender, ordenados por nombre (404 si el Pokémon no existe) |
| GET | `/pokemons/{id}/evoluciones` | Cadena evolutiva completa a la que pertenece el Pokémon (404 si no existe); ver abajo |
| GET/POST | `/pokemons/importar-rango?total=N&movimientos=M` | Importar desde PokeAPI |
| GET/POST | `/pokemons/importar-todo` | Importación rápida (50 + 50) |

### Filtros combinados en `GET /pokemons`
Todos son opcionales y se combinan con **Y**. Ejemplo: `/pokemons?tipo=fire&generacion=1&sort=velocidad,desc`.

| Parámetro | Qué filtra | Valor inválido |
|---|---|---|
| `nombre` | Texto parcial, sin importar mayúsculas, en `nombre` o `nombreEs` | — |
| `generacion` | 1 a 9 | 400 |
| `tipo` | Nombre del tipo en inglés (`fire`, `water`…), sin importar mayúsculas. Un solo tipo por petición | Tipo inexistente → 200 con `content` vacío |
| `region` | Nativos de la región **o** que aparecen en alguna de sus rutas (igual que `/pokemons/region/{nombre}`) | Región inexistente → `content` vacío |
| `obtencion` | Forma de obtención, con o sin tilde (`fosil` = `Fósil`) | 400 con la lista de valores válidos |

- Un parámetro vacío (`?tipo=&generacion=1`) se ignora, como si no se hubiera enviado.
- Sin filtros, responde igual que el listado normal.
- Se pueden combinar con `page`, `size` y `sort`. `page.totalElements` cuenta Pokémon distintos (sin duplicados).
- Los endpoints de un solo filtro (`/buscar`, `/generacion/{n}`, `/tipo/{t}`, `/region/{r}`, `/obtencion/{o}`) siguen funcionando y dan el mismo resultado que su parámetro equivalente.

Ejemplo de Pokémon:
```json
{
  "idPokedex": 25,
  "nombre": "pikachu",
  "nombreEs": "Pikachu",
  "descripcion": "Cuando se enfada, descarga al instante la energía...",
  "generacion": 1,
  "region": "Kanto",
  "tipoObtencion": "Inicial",
  "formaObtencion": "Pokémon inicial: se elige al comenzar la aventura en la región de Kanto. También aparece en 12 lugares de Kanto, Johto y Hoenn.",
  "imagenUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/.../25.png",
  "altura": 0.4,
  "peso": 6.0,
  "ps": 35,
  "ataque": 55,
  "defensa": 40,
  "ataqueEspecial": 50,
  "defensaEspecial": 50,
  "velocidad": 90,
  "totalEstadisticas": 320,
  "tipos": [{ "id": 1, "nombre": "electric" }],
  "rutas": [{ "idRuta": 3, "nombre": "Bosque Verde", "region": "Kanto", "descripcion": "...", "idPokeapi": 321 }]
}
```
`nombre` es el identificador de la PokeAPI (slug en inglés, no cambia); `nombreEs` es el nombre en español para mostrar (si la PokeAPI no lo tiene, el de inglés). `nombreEs` puede venir en `null` en registros importados antes de que existiera el campo: se llena al volver a importar.

Las estadísticas son las base de la PokeAPI; `totalEstadisticas` es su suma (la calcula el servidor). Los movimientos no van en este JSON porque son muchos: se piden aparte en `/pokemons/{id}/movimientos`.

### Cadena evolutiva — `GET /pokemons/{id}/evoluciones`
Devuelve la cadena **completa**, como árbol, empezando por su etapa base: `/pokemons/1/evoluciones`, `/2/…` y `/3/…` responden lo mismo. No está paginada.

```json
{
  "idPokedex": 1, "nombre": "bulbasaur", "nombreEs": "Bulbasaur",
  "imagenUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/.../1.png",
  "condicion": null,
  "evoluciones": [
    {
      "idPokedex": 2, "nombre": "ivysaur", "nombreEs": "Ivysaur", "imagenUrl": "https://.../2.png",
      "condicion": "Nivel 16",
      "evoluciones": [
        { "idPokedex": 3, "nombre": "venusaur", "nombreEs": "Venusaur", "imagenUrl": "https://.../3.png",
          "condicion": "Nivel 32", "evoluciones": [] }
      ]
    }
  ]
}
```
- `evoluciones` son las etapas a las que evoluciona; siempre es un arreglo (vacío en la última etapa). Las ramas son varios elementos: Eevee tiene 8, ordenados por `idPokedex`.
- `condicion` es cómo se llega a esa etapa, en español ("Nivel 16", "Usando Piedra Agua", "Por intercambio", "Subiendo de nivel, con amistad alta, de día"). Es `null` en la etapa base. Si hay varias formas, van unidas con " o ". Los nombres de lugares y de tipos pueden salir en inglés.
- Las variantes de una misma especie (Wormadam Planta/Arena/Basura, los 63 Alcremie, Meowstic macho/hembra) no se distinguen: la condición es la de la especie. El género solo se menciona si la evolución es exclusiva de uno ("Nivel 20, solo hembras").
- Las formas regionales no se incluyen: Slowbro sale con "Nivel 37", sin la vía de Slowpoke de Galar. Si una etapa solo se alcanza desde una forma regional, se indica: "Nivel 28 (solo Meowth de Galar)" en Perrserker.
- `nombreEs` e `imagenUrl` son `null` si el Pokémon de esa etapa no está importado; `idPokedex` y `nombre` vienen siempre.
- Un Pokémon sin evoluciones (Tauros), o cuya cadena todavía no se ha importado, devuelve una sola etapa con `evoluciones: []`.
- Las cadenas se guardan al importar: tras actualizar el backend hay que volver a importar para llenarlas.

## Movimientos — `/movimientos`
Las listas son paginadas, con el mismo formato `{ content, page }` y los mismos parámetros que las de Pokémon (`?page=0&size=20&sort=nombre,asc`, máximo 200 por página). Si no se envía `sort`, se ordenan por `nombre`.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/movimientos` | Listado paginado |
| GET | `/movimientos/{id}` | Detalle (404 si no existe) |
| GET | `/movimientos/buscar?nombre=...` | Búsqueda paginada por nombre (parcial, sin importar mayúsculas); busca en `nombre` y en `nombreEs` (`trueno` encuentra `thunder-punch`). Nombre vacío → 400 |
| GET | `/movimientos/categoria/{Físico\|Especial\|Estado}` | Filtrar por categoría (paginado) |
| GET | `/movimientos/competitivos` | Movimientos con potencia > 80 (paginado) |
| POST | `/movimientos` | Crear (`nombre`, `categoria`, `potencia`; `nombreEs` opcional) |
| PUT | `/movimientos/{id}` | Actualizar (si no se envía `nombreEs`, se conserva el que tenía) |
| DELETE | `/movimientos/{id}` | Eliminar |

Ejemplo de movimiento:
```json
{ "idMovimiento": 9, "nombre": "thunder-punch", "nombreEs": "Puño Trueno", "categoria": "Físico", "potencia": 75, "esCompetitivo": false }
```
`nombre` es el slug en inglés de la PokeAPI y `nombreEs` el nombre en español (o el de inglés si no hay traducción; `null` si el movimiento se creó a mano sin él o aún no se ha reimportado). Al reimportar, los movimientos que ya existen actualizan su `nombreEs`.

`esCompetitivo` lo calcula el servidor: es `true` si la potencia es mayor a 80.

## Rutas — `/rutas`
`/rutas` y `/rutas/region/{nombre}` son paginadas (`{ content, page }`, `?page=0&size=20&sort=nombre,asc`, máximo 200 por página). Si no se envía `sort`, se ordenan por `nombre`. Para llenar un desplegable con todas las rutas de una región, pide `size=200` y revisa `page.totalPages` por si la región tiene más (Kanto tiene 88).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/rutas` | Listado paginado |
| GET | `/rutas/regiones` | Nombres de las regiones de origen (Kanto … Paldea); arreglo, sin paginar |
| GET | `/rutas/region/{nombre}` | Rutas de una región (paginado) |
| GET | `/rutas/{id}` | Detalle (404 si no existe) |
| POST | `/rutas` | Crear (`nombre`, `region`, `descripcion`) |
| PUT | `/rutas/{id}` | Actualizar |

## Tipos — `/tipos`
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/tipos` | Todos los tipos guardados |

## Formato de los demás recursos

Siguen siendo arreglos JSON sin paginar: `/pokemons/{id}/movimientos`, `/rutas/regiones` y `/tipos`. Todas las demás listas usan `{ content, page }`.

```jsonc
// Ruta
{ "idRuta": 3, "nombre": "Bosque Verde", "region": "Kanto", "descripcion": "...", "idPokeapi": 321 }

// Tipo
{ "id": 8, "nombre": "electric" }

// Respuesta de una importación
{
  "mensaje": "Importación finalizada",
  "resultados": [
    { "recurso": "pokemons", "importados": 151, "omitidos": 0, "fallidos": 0 },
    { "recurso": "rutas", "importados": 12, "omitidos": 190, "fallidos": 0 },
    { "recurso": "movimientos-aprendidos", "importados": 30, "omitidos": 420, "fallidos": 0 },
    { "recurso": "cadenas-evolutivas", "importados": 78, "omitidos": 0, "fallidos": 0 },
    { "recurso": "movimientos", "importados": 0, "omitidos": 0, "fallidos": 0 }
  ]
}
```
En una importación, `importados` son los registros creados, `omitidos` los que ya existían y `fallidos` los que no se pudieron traer de la PokeAPI. En `pokemons` y `cadenas-evolutivas`, `importados` también cuenta los que solo se actualizaron. Los números del ejemplo son ilustrativos.

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

## Notas sobre los datos

- `region` del Pokémon es su **región de origen** (según su generación).
- `rutas` son los **lugares reales** de los juegos donde aparece en estado salvaje, sacados de `/pokemon/{id}/encounters` de la PokeAPI. Incluye lugares de todas las regiones (por ejemplo, Pikachu aparece en Kanto y también en otras regiones).
- Algunos Pokémon no tienen rutas porque la PokeAPI no tiene encuentros para ellos: iniciales, legendarios de evento (como Mew) y casi todos los de Galar y Paldea. Para esos casos están `tipoObtencion` y `formaObtencion`, que explican cómo se consigue cada Pokémon.

## Formas de obtención (`tipoObtencion`)

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
