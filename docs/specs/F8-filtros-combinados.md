# F8 · Filtros combinados

**Estado:** Implementada · criterios pendientes de prueba automática (U5)

## Historia
Como fan de Pokémon, quiero combinar filtros (por ejemplo, tipo fuego de la generación 1) para encontrar exactamente lo que busco.

## Comportamiento
1. `GET /api/pokemons` acepta parámetros opcionales que se combinan con Y: `nombre`, `generacion`, `tipo`, `region`, `obtencion`.
2. Sin parámetros, se comporta exactamente como hoy (F1).
3. Mantiene la paginación, el `sort` y el formato `{ content, page }`.
4. Los endpoints de F2 siguen funcionando, para no romper el frontend.
5. Cada parámetro se comporta igual que su endpoint de F1/F2: `nombre` busca en `nombre` y `nombreEs`; `region` incluye nativos y Pokémon que aparecen en rutas de la región; `obtencion` acepta el valor con o sin tilde.
6. Un parámetro vacío (`?tipo=`) se ignora, como si no se hubiera enviado.

## Criterios de aceptación
- [ ] `GET /api/pokemons?tipo=fire&generacion=1` solo devuelve Pokémon de tipo `fire` y de generación 1 (incluye `charmander`).
- [ ] `GET /api/pokemons?generacion=10` responde 400.
- [ ] `GET /api/pokemons?nombre=char&obtencion=inicial` incluye `charmander` y no `charmeleon`.
- [ ] `GET /api/pokemons` sin parámetros devuelve lo mismo que antes del cambio.
- [ ] `page.totalElements` es correcto cuando se combinan `region` y `tipo` (sin duplicados).
- [ ] Cada filtro suelto da el mismo `page.totalElements` que su endpoint de F2 (ej. `?generacion=3` y `/generacion/3`).
- [ ] `GET /api/pokemons?tipo=&generacion=` devuelve lo mismo que sin parámetros.
- [ ] `GET /api/pokemons?obtencion=xyz` responde 400 y el mensaje lista los valores válidos.

## Fuera de alcance
Rangos de estadísticas (ej. `velocidad > 100`), operadores O entre filtros, filtrar por dos tipos a la vez (`?tipo=fire&tipo=flying`), filtrar por ruta (sigue en `/pokemons/ruta/{idRuta}`).
