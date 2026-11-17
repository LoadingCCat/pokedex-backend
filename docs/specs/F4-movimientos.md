# F4 · Movimientos

**Estado:** Implementada · criterios con pruebas automáticas (ver `docs/pruebas.md`)

## Historia
Como fan de Pokémon, quiero consultar los movimientos y ver cuáles puede aprender cada Pokémon, para conocer sus opciones de combate.

## Comportamiento
1. `GET /api/movimientos`, `/{id}`, `/buscar?nombre=`, `/categoria/{Físico|Especial|Estado}` y `/competitivos`. Las listas son paginadas (`{ content, page }`, por defecto 20, máximo 200) y, sin `sort`, se ordenan por `nombre`.
2. `GET /api/pokemons/{id}/movimientos`: los movimientos que puede aprender un Pokémon, ordenados por nombre.
3. `POST`, `PUT /{id}` y `DELETE /{id}` sobre `/api/movimientos`.
4. `esCompetitivo` lo calcula el servidor: `true` si `potencia > 80`.
5. `tipo` (Fase 6 del front, discos de MT): el tipo del movimiento, sacado de `type` en `/move/{id}` de la PokeAPI, con el nombre en inglés de los tipos (`electric`). Acepta `null`. `POST` y `PUT` lo aceptan opcional y validan que sea uno de los 18 tipos (sin importar mayúsculas; se guarda en minúsculas); `PUT` sin `tipo` conserva el que tenía. Al reimportar, los movimientos existentes con `tipo` en `null` lo reciben; los que ya tienen uno no se tocan.

## Criterios de aceptación
- [ ] `GET /api/movimientos/categoria/especial` solo devuelve movimientos con `categoria = "Especial"`.
- [ ] `GET /api/movimientos/competitivos` solo devuelve movimientos con `potencia > 80`.
- [ ] `GET /api/movimientos` devuelve `{ content, page }` con 20 movimientos ordenados por `nombre`; `?size=500` devuelve como máximo 200.
- [ ] `GET /api/movimientos?sort=foo` y `GET /api/movimientos/buscar?nombre=a&sort=foo` responden 400.
- [ ] `GET /api/movimientos/buscar?nombre=` (vacío) responde 400.
- [ ] `GET /api/movimientos/99999` responde 404.
- [ ] `GET /api/pokemons/25/movimientos` devuelve la lista en orden alfabético e incluye `thunderbolt`.
- [ ] `GET /api/pokemons/99999/movimientos` responde 404.
- [ ] `POST` con `{"nombre":"prueba","categoria":"especial","potencia":90}` responde 201 con `categoria = "Especial"` y `esCompetitivo = true`.
- [ ] `POST` con `esCompetitivo: true` y `potencia: 10` guarda `esCompetitivo = false` (el servidor lo calcula).
- [ ] `POST` sin nombre, con una categoría inválida o con potencia negativa responde 400.
- [ ] `POST` con un nombre que ya existe (sin importar mayúsculas) responde 400.
- [ ] `PUT /api/movimientos/{id}` con potencia 100 actualiza el movimiento y pone `esCompetitivo = true`.
- [ ] `DELETE /api/movimientos/{id}` de un movimiento que aprenden varios Pokémon responde 204, y el movimiento desaparece de `/pokemons/{id}/movimientos`.
- [x] Tras reimportar, `GET /api/pokemons/25/movimientos` incluye `thunderbolt` con `tipo = "electric"`, y ningún movimiento importado queda con `tipo` en `null`.
- [x] `POST` con `"tipo": "FIRE"` guarda `tipo = "fire"`; con `"tipo": "luz"` responde 400.
- [x] `PUT` sin `tipo` conserva el que tenía.

## Fuera de alcance
Cómo aprende cada Pokémon el movimiento (nivel, MT, tutor) y en qué juego. Nombres en español (F6).
