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
| GET | `/pokemons/buscar?nombre=pika` | Búsqueda por nombre (parcial, sin importar mayúsculas ni tildes: `pikachú` encuentra `pikachu`); busca en `nombre` y en `nombreEs` |
| GET | `/pokemons/generacion/{1-9}` | Filtrar por generación |
| GET | `/pokemons/tipo/{nombre}` | Filtrar por tipo (`fire`, `water`, …) |
| GET | `/pokemons/region/{nombre}` | Pokémon nativos de la región **o** que aparecen en alguna de sus rutas (`kanto`, `johto`, `teselia`, …) |
| GET | `/pokemons/ruta/{idRuta}` | Pokémon que aparecen en una ruta |
| GET | `/pokemons/obtencion/{tipo}` | Filtrar por forma de obtención (`inicial`, `legendario`, `fosil`, …; con o sin tilde) |
| GET | `/pokemons/{id}` | Detalle (404 si no existe) |
| GET | `/pokemons/{id}/movimientos` | Movimientos que puede aprender, ordenados por nombre (404 si el Pokémon no existe) |
| GET | `/pokemons/{id}/evoluciones` | Cadena evolutiva completa a la que pertenece el Pokémon (404 si no existe); ver abajo |
| POST | `/pokemons` | **ADMIN.** Crear un Pokémon (201). Número ocupado → 409. Ver "Crear y editar Pokémon" |
| PUT | `/pokemons/{id}` | **ADMIN.** Editar (200; 404 si no existe). Queda con `editadoAMano: true` y la reimportación no lo pisa |
| DELETE | `/pokemons/{id}` | **ADMIN.** Borrar (204; 404 si no existe). La reimportación no lo vuelve a crear |
| GET/POST | `/pokemons/importar-rango?total=N&movimientos=M` | Inicia la importación desde la PokeAPI en segundo plano: responde 202 al instante (409 si ya hay una en curso). Uso interno; **ADMIN** |
| GET/POST | `/pokemons/importar-todo` | Igual, importación rápida (50 + 50). Uso interno; **ADMIN** |
| GET | `/pokemons/importacion` | Progreso de la importación en curso o de la última (ver "Formato de los demás recursos"). Uso interno |

### Filtros combinados en `GET /pokemons`
Todos son opcionales y se combinan con **Y**. Ejemplo: `/pokemons?tipo=fire&generacion=1&sort=velocidad,desc`.

| Parámetro | Qué filtra | Valor inválido |
|---|---|---|
| `nombre` | Texto parcial, sin importar mayúsculas ni tildes, en `nombre` o `nombreEs` | — |
| `generacion` | 1 a 9 | 400 |
| `tipo` | Nombre del tipo en inglés (`fire`, `water`…), sin importar mayúsculas. Se puede repetir hasta 2 veces (F14): `?tipo=fire&tipo=flying` devuelve los Pokémon que tienen **los dos**. Un valor vacío o repetido (`fire` y `FIRE`) cuenta una vez. | Tipo inexistente → 200 con `content` vacío; 3 o más tipos distintos → 400 |
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

`editadoAMano` es `true` si el Pokémon se creó o se editó desde el panel (F15); en los importados sin tocar es `null`. Al reimportar, uno editado conserva sus datos, tipos, rutas y forma de obtención.

### Crear y editar Pokémon — `POST /pokemons`, `PUT /pokemons/{id}` (F15, ADMIN)
```json
{ "nombreEs": "Pikachu", "ps": 35, "ataque": 55, "defensa": 40, "ataqueEspecial": 50, "defensaEspecial": 50,
  "velocidad": 90, "altura": 0.4, "peso": 6.0, "tipoObtencion": "Inicial", "tipos": ["electric"],
  "formaObtencion": "...", "descripcion": "...", "imagenUrl": "https://...", "rutas": [3, 7] }
```
- **Obligatorios:** `nombreEs`; las 6 estadísticas (enteros de 1 a 255); `altura` y `peso` (≥ 0); `tipoObtencion` (uno de los 9 valores, con o sin tilde); `tipos` (1 o 2 de `/tipos`).
- **Al crear, además:** `idPokedex` (entero > 0, libre), `nombre` (slug: minúsculas, números y guiones) y `generacion` (1–9; de ella sale `region`).
- **Opcionales:** `formaObtencion`, `descripcion`, `imagenUrl` (https) y `rutas` (ids que existan). En `PUT`, un opcional que no se envía se conserva; `nombre` no cambia.
- Un error de validación responde 400 con `campos`, un mensaje por cada campo mal (`"ataque": "Debe estar entre 1 y 255"`, `"ataque": "Debe ser un número entero"` si llega `"abc"`).

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
| GET | `/movimientos/buscar?nombre=...` | Búsqueda paginada por nombre (parcial, sin importar mayúsculas ni tildes); busca en `nombre` y en `nombreEs` (`trueno` encuentra `thunder-punch`, `psiquico` encuentra "Psíquico" y `puno` encuentra "Puño Trueno"). Nombre vacío → 400 |
| GET | `/movimientos/categoria/{Físico\|Especial\|Estado}` | Filtrar por categoría (paginado) |
| GET | `/movimientos/competitivos` | Movimientos con potencia > 80 (paginado) |
| POST | `/movimientos` | **ADMIN.** Crear (`nombre`, `categoria`, `potencia`; `nombreEs` y `tipo` opcionales; un `tipo` que no es uno de los 18 → 400) |
| PUT | `/movimientos/{id}` | **ADMIN.** Actualizar (si no se envía `nombreEs` o `tipo`, se conserva el que tenía) |
| DELETE | `/movimientos/{id}` | **ADMIN.** Eliminar |

Ejemplo de movimiento:
```json
{ "idMovimiento": 9, "nombre": "thunder-punch", "nombreEs": "Puño Trueno", "tipo": "electric", "categoria": "Físico", "potencia": 75, "esCompetitivo": false }
```
`tipo` es el tipo del movimiento, con el mismo nombre en inglés que los tipos de Pokémon (`electric`, `fire`…; ver `/tipos`). Es `null` en los movimientos creados a mano sin él y en los importados antes de que existiera el campo, hasta volver a importar. Al reimportar solo se llena si estaba en `null` (no pisa un `tipo` cambiado con PUT).
`nombre` es el slug en inglés de la PokeAPI y `nombreEs` el nombre en español (o el de inglés si no hay traducción; `null` si el movimiento se creó a mano sin él o aún no se ha reimportado). Al reimportar, los movimientos que ya existen actualizan su `nombreEs`.

`esCompetitivo` lo calcula el servidor: es `true` si la potencia es mayor a 80.

En `POST` y `PUT`, `nombre` y `nombreEs` pueden tener hasta 100 caracteres (más es 400 con `campos`). El cuerpo solo admite `nombre`, `nombreEs`, `tipo`, `categoria` y `potencia`; `idMovimiento` y `esCompetitivo` se ignoran.

## Rutas — `/rutas`
`/rutas` y `/rutas/region/{nombre}` son paginadas (`{ content, page }`, `?page=0&size=20&sort=nombre,asc`, máximo 200 por página). Si no se envía `sort`, se ordenan por `nombre`. Para llenar un desplegable con todas las rutas de una región, pide `size=200` y revisa `page.totalPages` por si la región tiene más (Kanto tiene 88).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/rutas` | Listado paginado |
| GET | `/rutas/regiones` | Nombres de las regiones de origen (Kanto … Paldea); arreglo, sin paginar |
| GET | `/rutas/region/{nombre}` | Rutas de una región (paginado) |
| GET | `/rutas/{id}` | Detalle (404 si no existe) |
| POST | `/rutas` | **ADMIN.** Crear (`nombre`, `region`, `descripcion`) |
| PUT | `/rutas/{id}` | **ADMIN.** Actualizar |
| DELETE | `/rutas/{id}` | **ADMIN.** Eliminar (204; 404 si no existe). La quita de los Pokémon que aparecían en ella. Si era un lugar importado (`idPokeapi`), la reimportación no lo vuelve a crear |

En `POST` y `PUT`, `nombre` admite hasta 100 caracteres, `region` hasta 50 y `descripcion` hasta 2000 (más es 400 con `campos`). `idRuta` e `idPokeapi` del cuerpo se ignoran.

El `nombre` de las rutas importadas está en español cuando se pudo verificar (de la PokeAPI o del artículo de WikiDex: "Bosque Verde", "Calle Victoria"); unos 90 lugares sin artículo verificado siguen en inglés. El campo no cambia de forma: solo su valor, tras reimportar.

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

// Respuesta de importar-rango / importar-todo: 202 Accepted, al instante
{
  "mensaje": "Importación iniciada. Consulta el avance en GET /api/pokemons/importacion",
  "progreso": { "estado": "en-curso", "fase": "pokemons", "procesados": 0, "total": 151, "...": "..." }
}

// GET /api/pokemons/importacion: importación terminada
{
  "estado": "terminada", "fase": null,
  "procesados": 151, "total": 151, "porcentaje": 100, "fallidos": 0,
  "inicio": "2026-11-14T21:00:00", "fin": "2026-11-14T21:03:10", "mensaje": null,
  "resultados": [
    { "recurso": "pokemons", "importados": 151, "omitidos": 0, "fallidos": 0 },
    { "recurso": "rutas", "importados": 12, "omitidos": 190, "fallidos": 0 },
    { "recurso": "movimientos-aprendidos", "importados": 30, "omitidos": 420, "fallidos": 0 },
    { "recurso": "cadenas-evolutivas", "importados": 78, "omitidos": 0, "fallidos": 0 },
    { "recurso": "movimientos", "importados": 0, "omitidos": 0, "fallidos": 0 }
  ]
}
```
La importación corre en segundo plano (F10). `estado` es `sin-iniciar` (nada desde que arrancó el servidor), `en-curso`, `terminada` o `error` (con un `mensaje` genérico; el detalle va al log). `fase` es `pokemons` o `movimientos`, y `procesados`/`total`/`porcentaje` se refieren a esa fase. `resultados` solo viene al terminar. El progreso vive en memoria: tras reiniciar el servidor vuelve a `sin-iniciar`.

En `resultados`, `importados` son los registros creados, `omitidos` los que ya existían y `fallidos` los que no se pudieron traer de la PokeAPI. En `pokemons` y `cadenas-evolutivas`, `importados` también cuenta los que solo se actualizaron. Los números del ejemplo son ilustrativos.

## Autenticación — `/auth` (F15)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/auth/login` | `{ "usuario", "contrasena" }` → 200 `{ "token", "expiraEn", "usuario", "rol" }`. Credenciales incorrectas → 401 ("Usuario o contraseña incorrectos", sin decir cuál falló). Campo vacío o sin cuerpo → 400; `usuario` de más de 100 o `contrasena` de más de 128 caracteres → 400 con `campos`. Al quinto intento fallido en un minuto desde la misma IP, los siguientes responden 429 hasta que pase el minuto (aunque la contraseña sea correcta); los logins correctos no cuentan |

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "expiraEn": "2026-11-17T18:30:00Z", "usuario": "admin", "rol": "ADMIN" }
```
- El token es un JWT HS256 que dura 2 horas. Se envía en la cabecera `Authorization: Bearer <token>`.
- **Públicos (sin token):** todos los `GET` (incluido `GET /health`), salvo `importar-rango` e `importar-todo`, y `POST /auth/login` y `POST /pokemons/identificar` (F20). El frontend de consulta no cambia.
- **Solo ADMIN:** `POST`, `PUT` y `DELETE` (movimientos, rutas…; salvo `POST /auth/login` y `POST /pokemons/identificar`) y la importación, también por GET. Sin token, o con uno inválido o vencido → **401**; con un token sin el rol ADMIN → **403**. Los dos con el formato de error de siempre.
- Un `GET` público con un token inválido también responde 401: si se envía `Authorization`, tiene que ser válido.
- **CORS:** el navegador solo puede llamar a la API desde los orígenes de `CORS_ORIGENES` (por defecto `http://localhost:3000` y `http://127.0.0.1:3000`). Antes era `*`. Las llamadas desde un servidor (como los route handlers de Next.js) no dependen de CORS.

## Identificar por foto — `POST /pokemons/identificar` (F20)

**Quién:** cualquiera, sin token. Límite: 10 peticiones por minuto por IP. Si el servidor del frontend llama en nombre del navegador, la IP es la que declara en `X-Cliente-IP`, pero solo se acepta con `X-Proxy-Secreto` igual a `PROXY_SECRETO` del backend (el mismo criterio vale para el login); si no, la de la conexión.

**Entrada:** `multipart/form-data` con un único campo:

| Campo | Tipo | Regla |
|---|---|---|
| `foto` | archivo | Obligatorio. JPEG, PNG o WEBP, comprobado por los primeros bytes del archivo (no solo por `Content-Type`). Máximo 5 MB |

**200 OK**
```json
{
  "identificado": {
    "idPokedex": 4,
    "nombre": "charmander",
    "nombreEs": "Charmander",
    "tipos": [{ "id": 2, "nombre": "fire" }],
    "totalEstadisticas": 309,
    "imagenUrl": "https://…"
  },
  "confianza": 0.93,
  "similares": [
    { "idPokedex": 155, "nombre": "cyndaquil", "nombreEs": "Cyndaquil", "tipos": [{ "id": 2, "nombre": "fire" }], "totalEstadisticas": 309, "imagenUrl": "https://…", "motivo": "Comparte el tipo Fuego y tiene el mismo total" },
    { "idPokedex": 390, "nombre": "chimchar",  "nombreEs": "Chimchar",  "tipos": [{ "id": 2, "nombre": "fire" }], "totalEstadisticas": 309, "imagenUrl": "https://…", "motivo": "Comparte el tipo Fuego y tiene el mismo total" },
    { "idPokedex": 255, "nombre": "torchic",   "nombreEs": "Torchic",   "tipos": [{ "id": 2, "nombre": "fire" }], "totalEstadisticas": 310, "imagenUrl": "https://…", "motivo": "Comparte el tipo Fuego y su total difiere en 1" }
  ]
}
```
- `tipos` tiene la misma forma que en el detalle del Pokémon: `[{ "id", "nombre" }]`. Los `id` del ejemplo son ilustrativos.
- `identificado` y `similares` se leen de PostgreSQL. La IA solo aporta el slug y la confianza.
- `similares` tiene siempre 3 elementos y nunca incluye al identificado (criterios en F20). Los números del ejemplo son ilustrativos.
- La foto no se guarda.

**Errores** (con el formato de error de siempre):

| Código | Cuándo | `mensaje` |
|---|---|---|
| 400 | Falta `foto`, está vacía o el `multipart` está mal formado | «Envía una foto en el campo foto» |
| 400 | No es JPEG, PNG ni WEBP | «La foto debe ser JPG, PNG o WEBP» |
| 400 | Pesa más de 5 MB | «La foto pesa más de 5 MB» |
| 422 | La IA no reconoce un Pokémon, su confianza es menor que 0,6 o el slug no existe en la base | «No reconocimos un Pokémon en la foto. Prueba con otra más clara» |
| 429 | Más de 10 peticiones por minuto desde la misma IP (con `Retry-After: 60`) | «Demasiadas fotos seguidas. Espera un minuto» |
| 502 | El servicio de visión no respondió a tiempo, falló o no está configurado | «El reconocimiento no está disponible en este momento» |

Ningún mensaje incluye la clave, la URL del proveedor, la respuesta cruda de la IA ni trazas: eso va solo al log.

## Salud — `GET /health`

Público, no consulta la base. Sirve para despertar el servidor en Render y para el monitoreo.

**200 OK**
```json
{ "estado": "ok" }
```

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
| 400 | Parámetro inválido o faltante, JSON mal formado, datos inválidos en POST/PUT, `sort` por un campo que no existe. En POST/PUT de Pokémon, movimientos y rutas, el JSON suma `campos`: `{ "nombre": "..." }`, un mensaje por cada campo mal (F15) |
| 401 | Falta el token, es inválido o venció; o usuario o contraseña incorrectos en el login (F15) |
| 403 | El token es válido, pero sin el rol ADMIN (F15) |
| 404 | La URL no existe o el ID no existe |
| 405 | Método HTTP no permitido en esa ruta |
| 406 | El `Accept` pide un formato que la API no produce (solo `application/json`) |
| 409 | Se pidió una importación mientras otra sigue en curso, o se creó un Pokémon con un número ocupado |
| 415 | El `Content-Type` del cuerpo no es JSON (p. ej. `text/plain` en `POST /auth/login`) |
| 422 | La petición es válida, pero no se pudo procesar: p. ej., no hay un Pokémon en la foto (F20) |
| 429 | Demasiadas peticiones desde la misma IP: más de 10 fotos por minuto (F20) o 5 intentos de login fallidos por minuto. Lleva la cabecera `Retry-After: 60` (segundos) |
| 500 | Error inesperado del servidor (el detalle solo queda en el log, no en la respuesta) |
| 502 | Falló un servicio externo: el servicio de visión no respondió, falló o no está configurado (F20) |

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

---

## Ampliación F11–F15 (aprobada, pendiente de implementar)

Lo de esta sección **todavía no existe en la API**. Cada parte pasa a su sección de arriba cuando se implementa su tarea (B.29–B.38). Todo se **agrega**: los campos y endpoints actuales responden igual. Specs: `docs/specs/F11`–`F15`.

### Filtro nuevo en `GET /pokemons` (F13)
| Parámetro | Qué filtra | Valor inválido |
|---|---|---|
| `juego` | Slug de la versión (`red`, `heartgold`; ver `/juegos`): Pokémon presentes en ese juego (encuentros en la versión o aprendizaje en su grupo) | 400 con la lista de valores válidos |

### Endpoints nuevos
| Método | Ruta | Descripción | Spec |
|---|---|---|---|
| GET | `/pokemons/{id}/habilidades` | Arreglo ordenado por `slot` (404 si el Pokémon no existe) | F11 |
| GET | `/pokemons/{id}/ubicaciones?juego=` | Encuentros por juego, con método, probabilidad y niveles (404 / 400) | F13 |
| GET | `/juegos` | Juegos con datos, en orden de lanzamiento; arreglo sin paginar | F13 |

### Cambios que solo agregan
- `GET /pokemons/{id}/movimientos` acepta `?juego=` y `?metodo=` (`nivel`, `maquina`, `tutor`, `huevo`, `otros`). **Sin parámetros devuelve los mismos elementos de hoy** (Pikachu: 109). Cada elemento suma `precision`, `metodo`, `nivel`, `metodos`, `etiqueta` y `juego` (F12):
  ```json
  { "idMovimiento": 9, "nombre": "thunder-punch", "nombreEs": "Puño Trueno", "tipo": "electric",
    "categoria": "Físico", "potencia": 75, "esCompetitivo": false,
    "precision": 100,
    "metodo": { "clave": "tutor", "nombreEs": "Tutor" }, "nivel": null,
    "metodos": [{ "clave": "tutor", "nombreEs": "Tutor", "nivel": null }],
    "etiqueta": null,
    "juego": { "clave": "scarlet-violet", "nombreEs": "Escarlata y Púrpura" } }
  ```
  Sin `juego`, se usa el grupo más reciente en el que el Pokémon aprende movimientos por nivel (nunca Champions). `etiqueta` ∈ `"Aventura"` (por nivel, > 1, en ese juego), `"Competitivo"` (potencia > 80), `"Ambos"` o `null`. Los valores del ejemplo son ilustrativos.
- Movimiento: campo `precision` (0–100 o `null`), también en POST/PUT. Al reimportar solo se llena si está en `null`.

Ya implementado y movido a su sección: la autenticación (B.30), `DELETE /rutas/{id}` (B.31), `POST`/`PUT`/`DELETE /pokemons`, `editadoAMano` y `campos` en los errores (B.32).
