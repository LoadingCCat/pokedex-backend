# F13 · Ubicaciones por juego

**Estado:** Pendiente (requiere reimportar)

## Historia
Como jugador de un juego concreto, quiero ver qué Pokémon hay en mi juego y dónde, cómo y con qué probabilidad aparece cada uno (RF-04 y RF-07 de la entrevista).

## Comportamiento
1. Entidad `Juego`, una por versión de la PokeAPI (53): `clave` (slug, `heartgold`), `nombreEs` (de `names` en `es`: "Rojo", "Oro HeartGold", "Platino", "Blanco 2"), `generacion`, `grupo` (slug del grupo de versiones) y `orden`. Se cargan al importar desde `/version` y `/version-group`.
2. Entidad `Encuentro`, una fila por (Pokémon, lugar, versión, método): `ruta`, `juego`, `metodo` (slug), `metodoEs`, `probabilidad`, `nivelMin` y `nivelMax`. Sale de `version_details` de `/pokemon/{id}/encounters`.
   - Las áreas de un mismo lugar se agrupan en su `Ruta`, igual que hoy.
   - `probabilidad`: suma de `chance` de los detalles de ese método en el área, con `max_chance` como tope. Entre las áreas del lugar se toma la mayor. Es un porcentaje entero.
   - Los Claros Ocultos de la quinta generación (`hidden-grotto`) son un método más: "Claro Oculto".
   - Al importar se reemplazan los encuentros de ese Pokémon, por lotes.
3. `GET /api/juegos` devuelve un arreglo sin paginar de `{ clave, nombreEs, generacion, grupo }`, en orden de lanzamiento. Solo incluye los juegos con datos (encuentros o aprendizaje).
4. `GET /api/pokemons/{id}/ubicaciones[?juego=]` devuelve un arreglo sin paginar de `{ ruta: { idRuta, nombre, region }, juego: { clave, nombreEs }, metodo, metodoEs, probabilidad, nivelMin, nivelMax }`, ordenado por juego (`orden`) y por nombre del lugar. Si el Pokémon no existe, 404. Un juego inválido → 400 con la lista de valores válidos.
5. `GET /api/pokemons?juego=heartgold`: Pokémon **presentes** en ese juego, es decir, con encuentros en la versión **o** con aprendizaje en su grupo. Se usa también el aprendizaje porque la PokeAPI casi no tiene encuentros de Galar ni de Paldea (Sprigatito, Klawf y Gholdengo tienen 0): con solo encuentros, Escarlata/Púrpura saldría casi vacío. Se combina con Y con los demás filtros (`EXISTS` en `PokemonFiltros`), sin duplicados. Un juego inválido → 400. Si viene vacío, se ignora.
6. `rutas` del JSON del Pokémon y `/pokemons/ruta/{id}` no cambian.

## Criterios de aceptación
- [ ] `GET /api/juegos` incluye `red` ("Rojo", generación 1) y `heartgold` ("Oro HeartGold", generación 4), y Rojo va antes.
- [ ] `GET /api/pokemons/25/ubicaciones?juego=yellow` incluye el Bosque Verde con `probabilidad`, `nivelMin` y `nivelMax` (el valor exacto se anota tras reimportar).
- [ ] `GET /api/pokemons/41/ubicaciones?juego=red` incluye el Mt. Moon con `metodo = "walk"` y su `metodoEs`.
- [ ] Si la PokeAPI trae encuentros `hidden-grotto`, salen con `metodoEs: "Claro Oculto"`. Si no trae ninguno, queda anotado como limitación.
- [ ] `GET /api/pokemons?juego=red&tipo=electric` solo trae Pokémon eléctricos presentes en Rojo, y `totalElements` no cuenta duplicados.
- [ ] `GET /api/pokemons?juego=xyz` → 400 con los valores válidos. `?juego=` vacío da lo mismo que sin el parámetro.
- [ ] `GET /api/pokemons/99999/ubicaciones` → 404.
- [ ] Reimportar no duplica encuentros, y borrar una ruta (F15) borra sus encuentros.
- [ ] Front: el selector de juego vive en la URL (`?juego=`) y cambia el catálogo, las ubicaciones de la ficha y sus movimientos. Sin juego, todo se ve como hoy.

## Fuera de alcance
Encuentros que la PokeAPI no tiene (casi todo Galar y Paldea), filtrar por hora, clima o estación, mapas por juego y editar encuentros desde el panel.
