# F8 · Filtros combinados

**Estado:** Pendiente

## Historia
Como fan de Pokémon, quiero combinar filtros (por ejemplo, tipo fuego de la generación 1) para encontrar exactamente lo que busco.

## Comportamiento
1. `GET /api/pokemons` acepta parámetros opcionales que se combinan con Y: `nombre`, `generacion`, `tipo`, `region`, `obtencion`.
2. Sin parámetros, se comporta exactamente como hoy (F1).
3. Mantiene la paginación, el `sort` y el formato `{ content, page }`.
4. Los endpoints de F2 siguen funcionando, para no romper el frontend.

## Criterios de aceptación
- [ ] `GET /api/pokemons?tipo=fire&generacion=1` solo devuelve Pokémon de tipo `fire` y de generación 1 (incluye `charmander`).
- [ ] `GET /api/pokemons?generacion=10` responde 400.
- [ ] `GET /api/pokemons?nombre=char&obtencion=inicial` incluye `charmander` y no `charmeleon`.
- [ ] `GET /api/pokemons` sin parámetros devuelve lo mismo que antes del cambio.
- [ ] `page.totalElements` es correcto cuando se combinan `region` y `tipo` (sin duplicados).

## Fuera de alcance
Rangos de estadísticas (ej. `velocidad > 100`), operadores O entre filtros.
