# F11 · Habilidades

**Estado:** Pendiente (requiere reimportar)

## Historia
Como fan de Pokémon, quiero ver las habilidades de un Pokémon, y cuál es la oculta, para entender cómo se comporta en combate (RF-02 de la entrevista).

## Comportamiento
1. `GET /api/pokemons/{id}/habilidades` devuelve un arreglo, sin paginar, ordenado por `slot`:
   `{ idHabilidad, nombre, nombreEs, descripcion, descripcionEnIngles, oculta, slot }`. Si el Pokémon no existe, responde 404.
2. Entidad `Habilidad` (`nombre` = slug de la PokeAPI, como `static`; `nombreEs`; `descripcion`; `descripcionEnIngles`) y una relación `PokemonHabilidad` (pokémon, habilidad, `oculta`, `slot`).
3. Al importar se leen `abilities` de `/pokemon/{id}` y `/ability/{id}`, una vez por habilidad y por importación (caché de la importación).
   - `nombreEs` sale de `names` en `es`; si no está, de `en`.
   - `descripcion` sale de la última entrada `es` de `flavor_text_entries`; si no hay, de la de `en`, con `descripcionEnIngles: true`. `effect_entries` no se usa: no existe en español.
4. Las habilidades no van en el JSON del Pokémon: se piden aparte, como los movimientos y las evoluciones.
5. Reimportar reemplaza las relaciones de ese Pokémon. No duplica habilidades.

## Criterios de aceptación
- [ ] `GET /api/pokemons/25/habilidades` devuelve 2: `static` (slot 1, `oculta: false`) y `lightning-rod` (slot 3, `oculta: true`), con `nombreEs` en español.
- [ ] `GET /api/pokemons/133/habilidades` devuelve 3, ordenadas por `slot`, y la tercera es la oculta.
- [ ] Una habilidad sin texto en español trae el texto en inglés y `descripcionEnIngles: true` (prueba con la PokeAPI simulada).
- [ ] `GET /api/pokemons/99999/habilidades` responde 404 con el formato de error de siempre.
- [ ] Reimportar dos veces no duplica filas de `habilidad` ni de `pokemon_habilidad`.
- [ ] Ficha (P2): sección "Habilidades" en la que la oculta se marca con el texto "Oculta", no solo con color.

## Fuera de alcance
Buscar o filtrar Pokémon por habilidad, una página por habilidad y editar habilidades desde el panel (F15).
