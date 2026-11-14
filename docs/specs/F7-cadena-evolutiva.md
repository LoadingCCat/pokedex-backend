# F7 · Cadena evolutiva

**Estado:** Pendiente

## Historia
Como fan de Pokémon, quiero ver de qué evoluciona y en qué evoluciona un Pokémon, y cómo, para planear mi equipo.

## Comportamiento
1. `GET /api/pokemons/{id}/evoluciones` devuelve la cadena completa a la que pertenece el Pokémon (de `evolution_chain` en `/pokemon-species/{id}`).
2. Cada etapa trae `idPokedex`, `nombre`, `imagenUrl` y la condición para llegar a ella (nivel, piedra, intercambio, amistad…), en español.
3. Las ramas se representan como varias etapas en el mismo nivel (ej. Eevee).

## Criterios de aceptación
- [ ] `GET /api/pokemons/1/evoluciones` devuelve Bulbasaur → Ivysaur (nivel 16) → Venusaur (nivel 32).
- [ ] `GET /api/pokemons/2/evoluciones` devuelve la misma cadena que `/1/evoluciones`.
- [ ] `GET /api/pokemons/133/evoluciones` devuelve a Eevee con todas sus evoluciones como ramas.
- [ ] Un Pokémon sin evoluciones (ej. Tauros, 128) devuelve una cadena con una sola etapa.
- [ ] `GET /api/pokemons/99999/evoluciones` responde 404.
- [ ] Una etapa cuyo Pokémon no está importado aparece con su nombre, pero sin `imagenUrl`.

## Fuera de alcance
Megaevoluciones y formas regionales.
