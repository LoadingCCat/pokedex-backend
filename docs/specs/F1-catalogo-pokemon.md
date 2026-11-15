# F1 · Catálogo y ficha de Pokémon

**Estado:** Implementada · criterios con pruebas automáticas (ver `docs/pruebas.md`)

## Historia
Como fan de Pokémon, quiero recorrer el catálogo, buscar por nombre y abrir la ficha de un Pokémon para conocer sus datos.

## Comportamiento
1. `GET /api/pokemons` devuelve el catálogo paginado (`?page=&size=&sort=`). Si no se envía `sort`, todas las listas de Pokémon (catálogo, búsqueda y filtros de F2 y F8) se ordenan por `idPokedex` ascendente.
2. `GET /api/pokemons/buscar?nombre=` busca por coincidencia parcial del nombre, sin importar mayúsculas.
3. `GET /api/pokemons/{id}` devuelve la ficha: nombre, descripción en español, generación, región de origen, tipos, imagen, altura, peso, estadísticas base, rutas y forma de obtención.
4. Las respuestas paginadas usan el formato fijo `{ content, page }` (ver `docs/contratos/api.md`).

## Criterios de aceptación
- [ ] `GET /api/pokemons` responde 200 con 20 Pokémon en `content` y `page.number = 0`.
- [ ] `GET /api/pokemons` sin `sort` empieza en `bulbasaur` (1), `ivysaur` (2), `venusaur` (3); recorrer todas las páginas devuelve los 1025 sin repetir ni saltar ninguno.
- [ ] `GET /api/pokemons?size=500` devuelve como máximo 200 Pokémon.
- [ ] `GET /api/pokemons?sort=nombre,asc` devuelve los Pokémon en orden alfabético.
- [ ] `GET /api/pokemons?sort=campoInexistente` responde 400.
- [ ] `GET /api/pokemons/buscar?nombre=PIKA` incluye a `pikachu`.
- [ ] `GET /api/pokemons/buscar?nombre=` (vacío) responde 400.
- [ ] `GET /api/pokemons/buscar` sin el parámetro responde 400 con el mensaje "Falta el parámetro obligatorio 'nombre'".
- [ ] `GET /api/pokemons/25` responde 200 con `nombre = "pikachu"`, `region = "Kanto"` y `tipos` con `electric`.
- [ ] La ficha de `/api/pokemons/25` trae `ps=35`, `ataque=55`, `defensa=40`, `ataqueEspecial=50`, `defensaEspecial=50`, `velocidad=90` y `totalEstadisticas=320`.
- [ ] La ficha no incluye el campo `movimientos` (se piden en F4).
- [ ] `GET /api/pokemons/99999` responde 404 con el mensaje "Pokémon con id 99999 no existe".
- [ ] `GET /api/pokemons/abc` responde 400.

## Fuera de alcance
Crear, editar o borrar Pokémon (solo entran por importación, F3). Nombres en español (F6).
