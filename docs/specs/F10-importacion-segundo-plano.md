# F10 · Importación en segundo plano con progreso

**Estado:** Implementada · criterios pendientes de prueba automática (U5)

## Historia
Como integrante del equipo, quiero lanzar la importación de las 1025 especies y consultar cuánto lleva, sin dejar una petición HTTP abierta 20 minutos que el cliente puede cortar.

## Comportamiento
1. `POST /api/pokemons/importar-rango?total=N&movimientos=M` y `POST /api/pokemons/importar-todo` (también GET) **inician** la importación en segundo plano y responden de inmediato **202 Accepted**:
   ```json
   { "mensaje": "Importación iniciada. Consulta el avance en GET /api/pokemons/importacion", "progreso": { ... } }
   ```
2. `GET /api/pokemons/importacion` devuelve el progreso de la importación en curso o de la última:
   ```json
   {
     "estado": "en-curso", "fase": "pokemons",
     "procesados": 120, "total": 1025, "porcentaje": 11, "fallidos": 0,
     "inicio": "2026-11-14T21:00:00", "fin": null, "mensaje": null, "resultados": null
   }
   ```
   - `estado`: `sin-iniciar` (no ha habido importaciones desde que arrancó el servidor), `en-curso`, `terminada` o `error`.
   - `fase`: `pokemons` (Pokémon con sus rutas, movimientos que aprenden y cadenas) o `movimientos` (movimientos 1..M). `null` si no hay ninguna en curso.
   - `procesados`, `total` y `porcentaje` se refieren a la fase actual. `fallidos` son los Pokémon fallidos hasta el momento.
   - `resultados`: al terminar, la misma lista que antes devolvía la importación (`recurso`, `importados`, `omitidos`, `fallidos`). `null` mientras está en curso.
   - `mensaje`: solo con `estado = "error"`, genérico; el detalle va al log.
3. Solo una importación a la vez: si ya hay una en curso, responde **409** al instante, sin iniciar otra.
4. Las validaciones de `total` y `movimientos` siguen respondiendo 400 y no inician nada.
5. La importación corre en un hilo aparte, sin la sesión de BD de la petición (`open-in-view`): no lee relaciones lazy de las entidades y, si necesita las rutas que ya tenía un Pokémon, las pide con una consulta. Si un Pokémon falla, se cuenta como fallido y la importación sigue, igual que antes.
6. El progreso vive en memoria: si el servidor se reinicia, vuelve a `sin-iniciar` (lo ya importado queda en la BD; la importación es idempotente y se puede relanzar).

## Criterios de aceptación
- [ ] Antes de cualquier importación, `GET /api/pokemons/importacion` responde 200 con `estado = "sin-iniciar"`.
- [ ] `POST /api/pokemons/importar-rango?total=20&movimientos=0` responde 202 en menos de 2 s, con `progreso.estado = "en-curso"`.
- [ ] Mientras corre, `GET /api/pokemons/importacion` responde `estado = "en-curso"`, `fase = "pokemons"`, `total = 20` y `procesados` entre 0 y 20.
- [ ] Una segunda importación lanzada mientras corre la primera responde 409 con "Ya hay una importación en curso. Espera a que termine."
- [ ] Al terminar, `estado = "terminada"`, `fin` no es `null` y `resultados` tiene los recursos `pokemons`, `rutas`, `movimientos-aprendidos`, `cadenas-evolutivas` y `movimientos`.
- [ ] Al terminar, una importación nueva vuelve a responder 202.
- [ ] `total=0`, `total=1026`, `movimientos=-1` y `movimientos=901` responden 400 y `GET /importacion` no cambia.
- [ ] `importar-rango?total=1025&movimientos=0` termina con `importados = 1025` y `fallidos = 0` en `pokemons`.
- [ ] Si la importación se detiene por un error inesperado, `estado = "error"` y una importación nueva vuelve a ser aceptada.

## Fuera de alcance
Cancelar una importación en curso, guardar el progreso en la BD (sobrevivir a un reinicio) y encolar varias importaciones.
