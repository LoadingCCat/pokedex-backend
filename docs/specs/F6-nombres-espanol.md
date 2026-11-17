# F6 · Nombres en español

**Estado:** Implementada (commit `dbcfb78`) · criterios con pruebas automáticas (ver `docs/pruebas.md`)

## Historia
Como fan hispanohablante, quiero ver los nombres de los Pokémon y de los movimientos en español para entender la Pokédex sin traducir.

## Comportamiento
1. Campo `nombreEs` en `Pokemon` (de `/pokemon-species/{id}` → `names`, idioma `es`) y en `Movimiento` (de `/move/{id}` → `names`). Si no hay traducción, se usa el nombre en inglés.
2. El campo `nombre` (slug en inglés) **no cambia**: el frontend lo usa como identificador.
3. `/pokemons/buscar` y `/movimientos/buscar` buscan en `nombre` y en `nombreEs`.
4. Al reimportar, los movimientos que ya existen actualizan **solo** su `nombreEs`.
5. En `POST /movimientos`, `nombreEs` es opcional. En `PUT`, si no se envía, se conserva el que había.
6. `nombreEs` puede ser `null` en registros importados antes de este cambio, hasta volver a importar.
7. **Lugares (B.28):** el `nombre` de una ruta importada es su nombre en español: el de la PokeAPI si lo tiene y, si no, el título verificado de su artículo en WikiDex (`src/main/resources/lugares-es.properties`, por `idPokeapi`; generado desde `datos/enlaces-wikidex.json` del frontend con `scripts/nombres-lugares-es.mjs`). Del título se quita la región o el juego entre paréntesis ("Ruta 10 (Kanto)" → "Ruta 10"). Cuando WikiDex da dos nombres ("Pilares Lanza/Columnas Lanza" = Hispanoamérica/España), se usa el de **España**, el de los textos de los juegos (como los nombres de Pokémon y movimientos). Sin título verificado (unos 90 lugares), queda en inglés.
8. Al reimportar, una ruta que ya existe recibe el nombre en español solo si todavía tiene el nombre automático en inglés: una ruta renombrada con `PUT` no se toca. Las rutas creadas a mano (`idPokeapi` en `null`) tampoco.

## Criterios de aceptación
- [ ] Después de reimportar, `GET /api/pokemons/25` trae `nombre = "pikachu"` y `nombreEs = "Pikachu"`.
- [ ] El movimiento `thunder-punch` trae `nombreEs = "Puño Trueno"`.
- [ ] `GET /api/movimientos/buscar?nombre=trueno` incluye `thunder-punch`.
- [ ] `GET /api/pokemons/buscar?nombre=pika` devuelve los mismos resultados que antes del cambio.
- [ ] Un movimiento que ya existía antes del cambio tiene `nombreEs` después de reimportar, y su `categoria` y `potencia` no cambian.
- [ ] `PUT /api/movimientos/{id}` sin `nombreEs` conserva el `nombreEs` anterior.
- [ ] `POST /api/movimientos` con `"nombreEs": "   "` (solo espacios) lo guarda como `null`.
- [x] Tras reimportar, la ruta con `idPokeapi = 155` (Viridian Forest) se llama "Bosque Verde", la de Victory Road de Kanto "Calle Victoria" y la Ruta 10 de Kanto "Ruta 10".
- [x] Una ruta renombrada con `PUT` conserva su nombre al reimportar (prueba automática `alReimportarNoSeTocaUnaRutaRenombradaConPut`).
- [x] Ningún nombre de ruta acaba en "(Kanto)" ni lleva "/".

## Fuera de alcance
Traducir los tipos (el frontend ya muestra sus etiquetas en español) y la descripción de las rutas. La búsqueda sin importar tildes está en F9.
