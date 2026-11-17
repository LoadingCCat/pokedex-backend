# F12 · Aprendizaje de movimientos

**Estado:** Pendiente (requiere reimportar)

## Historia
Como jugador, quiero saber cómo y a qué nivel aprende cada movimiento un Pokémon en mi juego, qué precisión tiene y si me sirve para la aventura o para competir (RF-03 y RF-09 de la entrevista).

## Comportamiento
1. La entidad `Aprendizaje` (pokémon, movimiento, método, nivel, grupo de versiones) reemplaza la relación `ManyToMany` `pokemon_movimiento`. Sale de `version_group_details` de cada movimiento en `/pokemon/{id}`. Se guardan los 32 grupos de la PokeAPI (unas 630 000 filas para los 1025). Al importar, se borran y se vuelven a insertar las filas de ese Pokémon, por lotes (`hibernate.jdbc.batch_size`, `order_inserts`, `order_updates`, id con `SEQUENCE`).
2. `Movimiento.precision` (nullable; columna `precision_movimiento`) sale de `accuracy` de `/move/{id}`. Es `null` en los movimientos que no fallan (Danza Espada). Al reimportar solo se llena si está en `null`, para no pisar lo editado con PUT (regla de B.18). POST y PUT la aceptan: entero de 0 a 100 o `null`.
3. `GET /api/pokemons/{id}/movimientos[?juego=&metodo=]` sigue siendo un arreglo sin paginar, ordenado por `nombre`. Cada elemento trae los campos de hoy (`idMovimiento`, `nombre`, `nombreEs`, `tipo`, `categoria`, `potencia`, `esCompetitivo`) y además:
   - `precision`
   - `metodo`: `{ clave, nombreEs }`, el método principal en el juego (prioridad: nivel > MT/MO > tutor > huevo > otros)
   - `nivel`: el nivel si el método es por nivel; si no, `null`
   - `metodos`: todas las formas de aprenderlo en ese juego, `[{ clave, nombreEs, nivel }]`
   - `etiqueta`: `"Aventura"`, `"Competitivo"`, `"Ambos"` o `null`
   - `juego`: el grupo de versiones usado (`{ clave, nombreEs }`)
4. **Sin `juego`**, la lista es la misma de hoy: todos los movimientos que aprende en cualquier juego (Pikachu: 109). Los campos nuevos se calculan con el **juego por defecto**: el grupo más reciente en el que el Pokémon aprende algún movimiento subiendo de nivel. Así se descartan Champions, que solo tiene el método `train`, y las expansiones sin datos. Si el movimiento no se aprende en ese grupo, `metodo`, `nivel` y `metodos` son `null` o vacíos, y la etiqueta solo puede ser "Competitivo".
5. **Con `juego`** (slug de la versión, por ejemplo `red`, `heartgold`; ver F13), solo devuelve los movimientos que aprende en el grupo de esa versión. Un juego inválido responde 400 con la lista de valores válidos.
6. `metodo` ∈ {`nivel`, `maquina`, `tutor`, `huevo`, `otros`} filtra por el método principal. Uno inválido responde 400.
7. **Etiqueta de uso (RF-09)**, por Pokémon y movimiento:
   - **Aventura:** lo aprende subiendo de nivel, con nivel mayor que 1, en el juego elegido o en el de por defecto. Son los movimientos que el jugador casual va viendo mientras avanza en la historia.
   - **Competitivo:** potencia mayor que 80 (la regla de `esCompetitivo`).
   - **Ambos:** cumple las dos.
   - `null`: no cumple ninguna (por ejemplo, una MT de estado).
8. Nombres de los métodos en español, en un mapa fijo y con respaldo en `names` de la PokeAPI: Nivel (`level-up`), MT/MO (`machine`), Tutor (`tutor`), Huevo (`egg`), Recordador (`reminder`), Cambio de forma (`form-change`), Entrenamiento (`train`), Purificación, Sombra (XD) y los demás que traiga la PokeAPI.
9. Borrar un movimiento (`DELETE /movimientos/{id}`) borra antes sus filas de `aprendizaje`.
10. Transición: `pokemon_movimiento` se mantiene hasta comprobar la reimportación (B.37) y se quita después, tras el respaldo (B.38).

## Criterios de aceptación
- [ ] `GET /api/pokemons/25/movimientos` sigue devolviendo 109 elementos, con todos los campos de antes.
- [ ] `GET /api/pokemons/25/movimientos?juego=red` solo trae movimientos que Pikachu aprende en Rojo/Azul, y Rayo (`thunderbolt`) sale con `metodo.clave = "maquina"`.
- [ ] En `?juego=red`, Impactrueno (`thunder-shock`) tiene `metodo.clave = "nivel"` y su `nivel`.
- [ ] `?metodo=nivel` solo devuelve movimientos con método principal por nivel. `?metodo=xyz` → 400. `?juego=xyz` → 400 con la lista de juegos.
- [ ] Gruñido (`growl`) trae su `precision` (100) y Danza Espada (`swords-dance`) trae `precision: null`.
- [ ] Etiquetas: Trueno (`thunder`, potencia 110) aprendido por nivel → "Ambos"; Trueno por MT → "Competitivo"; Impactrueno por nivel → "Aventura"; una MT de estado → `null`.
- [ ] Sin `juego`, el grupo usado para Pikachu no es `champions` (tiene movimientos por nivel en un grupo anterior).
- [ ] Reimportar no pisa una `precision` editada con PUT y no duplica filas de `aprendizaje`.
- [ ] Borrar un movimiento borra sus filas de `aprendizaje` (sin error de clave foránea).
- [ ] Ficha (P2): la tabla muestra método, nivel, precisión y etiqueta, y se filtra por etiqueta y por método.

## Fuera de alcance
Movimientos de formas regionales o megaevoluciones como entradas aparte, editar el aprendizaje desde el panel (F15), y estrategias o sets competitivos (la etiqueta es solo la regla de arriba).
