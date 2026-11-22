# F5 · Rutas y regiones

**Estado:** Implementada · criterios con pruebas automáticas parciales (faltan: ver T-05 de `docs/calidad/interventoria.md`; ver `docs/pruebas.md`)

## Historia
Como fan de Pokémon, quiero explorar las regiones y los lugares de los juegos para saber qué Pokémon aparecen en cada uno.

## Comportamiento
1. `GET /api/rutas`, `/{id}` y `/region/{nombre}`. Las dos listas son paginadas (`{ content, page }`, por defecto 20, máximo 200) y, sin `sort`, se ordenan por `nombre`.
2. `GET /api/rutas/regiones`: las 9 regiones de origen, en orden de generación.
3. `POST /api/rutas` y `PUT /api/rutas/{id}` para rutas creadas a mano.
4. `GET /api/tipos`: los tipos guardados.

## Criterios de aceptación
- [ ] `GET /api/rutas/regiones` devuelve exactamente `["Kanto","Johto","Hoenn","Sinnoh","Teselia","Kalos","Alola","Galar","Paldea"]`.
- [ ] `GET /api/rutas/region/KANTO` devuelve lo mismo que `/region/kanto`, ordenado alfabéticamente.
- [ ] `GET /api/rutas` devuelve `{ content, page }` con 20 rutas ordenadas por `nombre`; `?sort=foo` responde 400.
- [ ] `GET /api/rutas/region/kanto?size=200` devuelve todas las rutas de Kanto en una sola página.
- [ ] `GET /api/rutas/99999` responde 404.
- [ ] `POST` con `{"nombre":"Ruta Prueba","region":"Kanto"}` responde 201 con `idPokeapi = null`.
- [ ] `POST` con `idPokeapi: 123` en el cuerpo guarda `idPokeapi = null`.
- [ ] `POST` sin nombre o sin región responde 400.
- [ ] `POST` repetido con el mismo nombre y región (sin importar mayúsculas) responde 400.
- [ ] `PUT /api/rutas/{id}` cambia nombre, región y descripción, y no toca `idPokeapi`.
- [ ] `DELETE /api/rutas/{id}` con token ADMIN responde 204 y la ruta deja de existir (404 si no existe; 401 sin token). Desde B.31 (F15) se puede borrar; el 405 original ya no aplica (ver `docs/decisiones.md`, 2026-11-22).
- [ ] `GET /api/tipos` incluye `electric` después de importar a Pikachu.

## Fuera de alcance
Mapas o coordenadas de los lugares. Nombres en español de los lugares que la PokeAPI solo tiene en inglés.
