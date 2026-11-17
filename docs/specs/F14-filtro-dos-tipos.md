# F14 · Filtro por dos tipos

**Estado:** Pendiente

## Historia
Como fan de Pokémon, quiero filtrar por dos tipos a la vez (por ejemplo, Fuego/Volador) para encontrar los Pokémon que tienen esa combinación (RF-08 de la entrevista).

## Comportamiento
1. `GET /api/pokemons?tipo=fire&tipo=flying` devuelve los Pokémon que tienen **los dos** tipos (lógica Y). Son dos `EXISTS` en `PokemonFiltros`, sin `JOIN`.
2. Con un solo `tipo`, el comportamiento es el de hoy.
3. Con 3 o más valores responde 400 ("Se pueden filtrar como mucho 2 tipos").
4. Un valor vacío (`?tipo=&tipo=fire`) se ignora. Un valor repetido (`tipo=fire&tipo=fire`) cuenta como uno.
5. Un tipo inexistente da un `content` vacío, como hoy.
6. Se combina con Y con los demás filtros (`nombre`, `generacion`, `region`, `obtencion`, `juego`), con `page`, `size` y `sort`.
7. `GET /api/pokemons/tipo/{t}` no cambia.

## Criterios de aceptación
- [ ] `GET /api/pokemons?tipo=fire&tipo=flying` incluye Charizard (#6) y Moltres (#146), y no incluye a Charmander (#4) ni a Pidgey (#16).
- [ ] `?tipo=flying&tipo=fire` da el mismo `totalElements` que `?tipo=fire&tipo=flying`.
- [ ] `GET /api/pokemons?tipo=fire` da el mismo total que antes del cambio.
- [ ] `?tipo=fire&tipo=flying&tipo=water` → 400 con un mensaje que explica el límite.
- [ ] `?tipo=fire&tipo=fire` da lo mismo que `?tipo=fire`. `?tipo=&tipo=fire` también.
- [ ] `?tipo=fire&tipo=flying&generacion=1&sort=velocidad,desc` combina los filtros, sin duplicados en `totalElements`.
- [ ] Front (P1): un segundo selector "Tipo 2", desactivado hasta elegir el primero. La URL queda como `?tipo=fire&tipo=flying`, los dos tipos salen como chips, y el total coincide con la API.

## Fuera de alcance
Lógica O entre tipos, más de dos tipos y distinguir el tipo principal del secundario (Fuego/Volador = Volador/Fuego).
