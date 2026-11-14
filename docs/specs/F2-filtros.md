# F2 · Filtros del catálogo

**Estado:** Implementada · criterios pendientes de prueba automática (U5)

## Historia
Como fan de Pokémon, quiero filtrar el catálogo por generación, tipo, región, ruta o forma de obtención para encontrar rápido lo que busco.

## Comportamiento
Todos los filtros devuelven una página con el mismo formato que F1.
1. `GET /api/pokemons/generacion/{1-9}`
2. `GET /api/pokemons/tipo/{nombre}`: nombre del tipo en inglés (`fire`, `water`…), sin importar mayúsculas.
3. `GET /api/pokemons/region/{nombre}`: Pokémon **nativos** de la región **o** que aparecen en alguna de sus rutas.
4. `GET /api/pokemons/ruta/{idRuta}`: Pokémon que aparecen en esa ruta.
5. `GET /api/pokemons/obtencion/{tipo}`: Inicial, Singular, Fósil, Legendario, Salvaje, Regalo, Huevo, Evolución o Sin datos; con o sin tilde.

## Criterios de aceptación
- [ ] `GET /api/pokemons/generacion/1` solo devuelve Pokémon con `generacion = 1`.
- [ ] `GET /api/pokemons/generacion/0` y `/generacion/10` responden 400 con el mensaje "La generación debe estar entre 1 y 9".
- [ ] `GET /api/pokemons/tipo/FIRE` devuelve lo mismo que `/tipo/fire`, y todos los resultados tienen el tipo `fire`.
- [ ] `GET /api/pokemons/tipo/inexistente` responde 200 con `content` vacío.
- [ ] `GET /api/pokemons/region/kanto` incluye a `bulbasaur` (nativo) y no repite ningún Pokémon.
- [ ] `page.totalElements` de `/region/kanto` coincide con el número real de Pokémon distintos.
- [ ] `GET /api/pokemons/ruta/{idRuta}` de una ruta existente solo devuelve Pokémon que tienen esa ruta en su ficha.
- [ ] `GET /api/pokemons/ruta/99999` responde 404.
- [ ] `GET /api/pokemons/obtencion/fosil` devuelve lo mismo que `/obtencion/Fósil`.
- [ ] `GET /api/pokemons/obtencion/inicial` incluye a `bulbasaur`, `charmander`, `squirtle` y `pikachu`.
- [ ] `GET /api/pokemons/obtencion/xyz` responde 400 y el mensaje lista los valores válidos.

## Fuera de alcance
Combinar varios filtros en una sola petición (F8).
