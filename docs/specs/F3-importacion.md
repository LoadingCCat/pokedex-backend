# F3 · Importación desde la PokeAPI

**Estado:** Implementada · criterios pendientes de prueba automática (U5)

## Historia
Como integrante del equipo, quiero llenar o actualizar la base de datos desde la PokeAPI con una sola petición, para no cargar datos a mano.

## Comportamiento
1. `POST /api/pokemons/importar-rango?total=N&movimientos=M` importa los Pokémon 1..N (con tipos, estadísticas, rutas reales y movimientos que aprenden) y los movimientos 1..M. También acepta GET para probarlo desde el navegador.
2. `POST /api/pokemons/importar-todo` es un atajo para importar 50 Pokémon y 50 movimientos.
3. Se puede repetir: actualiza lo que existe y no duplica.
4. Si falla un Pokémon o un movimiento, se cuenta como fallido y la importación sigue con el siguiente.
5. Uso interno: no es parte del contrato del frontend.

## Criterios de aceptación
- [ ] `importar-rango?total=10&movimientos=0` responde 200 con `resultados` para `pokemons`, `rutas`, `movimientos-aprendidos` y `movimientos`.
- [ ] Después de importar con `total=10`, `GET /api/pokemons` tiene `page.totalElements >= 10`.
- [ ] Repetir la misma importación no cambia `page.totalElements` (no duplica).
- [ ] `total=0`, `total=1026`, `movimientos=-1` y `movimientos=901` responden 400.
- [ ] Una segunda importación lanzada mientras la primera sigue en curso responde 409 con el mensaje "Ya hay una importación en curso. Espera a que termine."
- [ ] Al terminar una importación, una nueva vuelve a ser aceptada (no queda bloqueada).
- [ ] `importar-rango?total=1025&movimientos=0` termina con `importados = 1025` y `fallidos = 0` en `pokemons`, y `GET /api/pokemons/generacion/9` devuelve Pokémon.
- [ ] Después de importar, `/api/pokemons/25` tiene estadísticas, rutas y `tipoObtencion = "Inicial"`.
- [ ] Si una llamada a la PokeAPI no responde, se corta por timeout, ese Pokémon queda como fallido en `resultados` y la importación sigue con el siguiente.

## Fuera de alcance
Importación en segundo plano con consulta de progreso (pendiente en `docs/tareas.md`). Importar formas alternativas (megas, regionales).
