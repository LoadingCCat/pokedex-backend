# F9 · Búsqueda sin importar tildes

**Estado:** Implementada · criterios pendientes de prueba automática (U5)

## Historia
Como fan hispanohablante, quiero encontrar un Pokémon o un movimiento escribiendo su nombre sin tildes (`psiquico`, `puno`) para no depender de escribirlo exactamente igual que en el juego.

## Comportamiento
1. Las búsquedas por nombre ignoran mayúsculas **y tildes**, tanto en el texto buscado como en el nombre guardado:
   - `GET /api/pokemons/buscar?nombre=`
   - `GET /api/pokemons?nombre=` (filtro combinado de F8)
   - `GET /api/movimientos/buscar?nombre=`
2. Se compara en `nombre` (slug) y en `nombreEs`, igual que en F6.
3. Usa la función `unaccent` de PostgreSQL: quita tildes, diéresis y también convierte la `ñ` en `n` (`puno` encuentra "Puño Trueno").
4. La extensión `unaccent` se crea sola al arrancar el servidor (`schema.sql`, `CREATE EXTENSION IF NOT EXISTS unaccent`). No hay que hacer nada a mano en la BD.
5. El formato de las respuestas no cambia: solo pueden aparecer más resultados que antes.

## Criterios de aceptación
- [ ] `GET /api/movimientos/buscar?nombre=psiquico` incluye el movimiento `psychic` ("Psíquico").
- [ ] `GET /api/movimientos/buscar?nombre=psíquico` (con tilde) devuelve los mismos resultados que sin tilde.
- [ ] `GET /api/movimientos/buscar?nombre=puno` devuelve el mismo `page.totalElements` que `?nombre=puño`.
- [ ] `GET /api/pokemons/buscar?nombre=pikachú` devuelve a Pikachu (`idPokedex` 25).
- [ ] `GET /api/pokemons?nombre=pikachú&generacion=1` devuelve a Pikachu.
- [ ] `GET /api/pokemons/buscar?nombre=pika` devuelve los mismos resultados que antes del cambio.
- [ ] El servidor arranca dos veces seguidas sin error (crear la extensión es idempotente).

## Fuera de alcance
- Ignorar tildes en los filtros `tipo` y `region`: sus valores no llevan tildes. `obtencion` ya acepta `fosil` por `Fósil` (se normaliza en el controller).
- Búsqueda aproximada o con errores de escritura (`pikahcu`).
- Índices para la búsqueda: con unos 1000 Pokémon y 800 movimientos no hacen falta.
