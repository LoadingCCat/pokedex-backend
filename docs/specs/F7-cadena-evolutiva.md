# F7 · Cadena evolutiva

**Estado:** Implementada · criterios pendientes de prueba automática (U5)

## Historia
Como fan de Pokémon, quiero ver de qué evoluciona y en qué evoluciona un Pokémon, y cómo, para planear mi equipo.

## Comportamiento
1. `GET /api/pokemons/{id}/evoluciones` devuelve la cadena completa a la que pertenece el Pokémon (de `evolution_chain` en `/pokemon-species/{id}`).
2. Cada etapa trae `idPokedex`, `nombre`, `imagenUrl` y la condición para llegar a ella (nivel, piedra, intercambio, amistad…), en español.
3. Las ramas se representan como varias etapas en el mismo nivel (ej. Eevee): la respuesta es un árbol desde la etapa base, y cada etapa trae sus `evoluciones`.
4. Las cadenas se guardan en la BD durante la importación (tabla `evolucion`, una vez por cadena); el endpoint no consulta la PokeAPI. Si la cadena falla al importar, el Pokémon se importa igual.
5. Cada etapa trae también `nombreEs`. Si hay varias formas de evolucionar, la condición las une con " o ".
6. Un Pokémon cuya cadena todavía no se ha importado devuelve una sola etapa (él mismo), no un error.

## Criterios de aceptación
- [ ] `GET /api/pokemons/1/evoluciones` devuelve Bulbasaur → Ivysaur (nivel 16) → Venusaur (nivel 32).
- [ ] `GET /api/pokemons/2/evoluciones` devuelve la misma cadena que `/1/evoluciones`.
- [ ] `GET /api/pokemons/133/evoluciones` devuelve a Eevee con todas sus evoluciones como ramas.
- [ ] Un Pokémon sin evoluciones (ej. Tauros, 128) devuelve una cadena con una sola etapa.
- [ ] `GET /api/pokemons/99999/evoluciones` responde 404.
- [ ] Una etapa cuyo Pokémon no está importado aparece con su nombre, pero sin `imagenUrl`.

- [ ] `GET /api/pokemons/25/evoluciones` empieza en Pichu (172) aunque se pida a Pikachu.
- [ ] `GET /api/pokemons/abc/evoluciones` responde 400.
- [ ] La importación incluye el recurso `cadenas-evolutivas` con 0 fallidos.

## Fuera de alcance
Megaevoluciones y formas regionales. Traducir los nombres de lugares y tipos que aparecen dentro de la condición.
