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

## Criterios de aceptación
- [ ] Después de reimportar, `GET /api/pokemons/25` trae `nombre = "pikachu"` y `nombreEs = "Pikachu"`.
- [ ] El movimiento `thunder-punch` trae `nombreEs = "Puño Trueno"`.
- [ ] `GET /api/movimientos/buscar?nombre=trueno` incluye `thunder-punch`.
- [ ] `GET /api/pokemons/buscar?nombre=pika` devuelve los mismos resultados que antes del cambio.
- [ ] Un movimiento que ya existía antes del cambio tiene `nombreEs` después de reimportar, y su `categoria` y `potencia` no cambian.
- [ ] `PUT /api/movimientos/{id}` sin `nombreEs` conserva el `nombreEs` anterior.
- [ ] `POST /api/movimientos` con `"nombreEs": "   "` (solo espacios) lo guarda como `null`.

## Fuera de alcance
Traducir rutas (ya usan el español cuando la PokeAPI lo tiene) y tipos. La búsqueda sin importar tildes está en F9.
