# F19 · Debilidades, resistencias y naturalezas

**Estado:** Implementada (solo frontend: FE.46–FE.48) · criterios con pruebas automáticas (el de 360 px, en E2E; ver `docs/pruebas.md`)

## Historia
Como jugador, quiero ver en la ficha a qué tipos es débil o resistente un Pokémon y consultar qué hace cada naturaleza, para armar mi equipo sin salir de la Pokédex.

## Comportamiento
1. **Sin backend ni reimportación.** Las dos partes salen de datos fijos del frontend: no hay endpoints nuevos, el contrato no cambia y funcionan igual con el mock (Vercel) y con la API local.
2. **Tabla de efectividad** (`lib/efectividad-tipos.ts`): los 18 tipos, con los multiplicadores vigentes desde la 6.ª generación (×2, ×½, ×0; el resto ×1). Para un Pokémon con dos tipos, el multiplicador de cada tipo atacante es el producto de los dos (×4, ×2, ×1, ×½, ×¼, ×0).
3. **Ficha (P2):** sección «Debilidades y resistencias», después de «Estadísticas base». Agrupa los tipos atacantes en «Muy débil (×4)», «Débil (×2)», «Resiste (×½)», «Resiste mucho (×¼)» e «Inmune (×0)», con la insignia de cada tipo. Los grupos vacíos no aparecen; los ×1 no se listan. El multiplicador va en texto, no solo en color. No se consideran habilidades (Levitación…) ni objetos.
4. **Naturalezas (P11, `/naturalezas`):** las 25 naturalezas desde `datos/naturalezas.json` (nombre en español, slug en inglés, estadística que sube y la que baja). Tabla con «Naturaleza», «Sube (+10 %)» y «Baja (−10 %)»; las 5 neutras dicen «—» en las dos columnas y se indica que no cambian nada. Se enlaza desde la navegación principal.

## Criterios de aceptación
- [x] `/pokemon/6` (Charizard, Fuego/Volador): ×4 Roca; ×2 Agua y Eléctrico; ×½ Fuego, Lucha, Acero y Hada; ×¼ Planta y Bicho; ×0 Tierra.
- [x] `/pokemon/25` (Pikachu): ×2 Tierra; ×½ Eléctrico, Volador y Acero; sin inmunidades.
- [x] `/pokemon/94` (Gengar, Fantasma/Veneno): ×0 Normal y Lucha; ×2 Tierra, Psíquico, Fantasma y Siniestro; ×½ Planta y Hada; ×¼ Bicho y Veneno.
- [x] Prueba automática: la tabla tiene 18 × 18 entradas y los tres casos anteriores dan esos grupos.
- [x] `/naturalezas` lista 25 naturalezas; «Firme» sube Ataque y baja Ataque especial; «Miedosa» sube Velocidad y baja Ataque; hay 5 neutras.
- [x] Las dos funcionan en el build sin `API_URL` y sin scroll horizontal a 360 px.

## Fuera de alcance
Habilidades que cambian la efectividad, Teratipos, sabores favoritos de las naturalezas, calcular estadísticas finales (IV/EV) y guardar estos datos en el backend.
