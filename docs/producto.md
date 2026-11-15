# Producto · Pokedex+

## Problema
La información del universo Pokémon está repartida en muchas fuentes, casi siempre en inglés y sin filtros útiles: no hay un lugar donde ver en español qué es un Pokémon, dónde se captura, cómo se consigue y qué movimientos aprende.

## Usuario
- **Principal:** estudiantes universitarios de ingeniería y desarrollo (como el equipo de la Universidad Santo Tomás, USTA) y entusiastas de Pokémon que buscan una herramienta técnica y limpia para consultar datos de la franquicia.
- **Secundario:** desarrolladores frontend y evaluadores académicos que necesitan consumir una API REST limpia, documentada y funcional, con datos estructurados.

## Objetivo del MVP
Una Pokédex web en español donde un usuario encuentra cualquier Pokémon de las 9 generaciones (los 1025 de la Pokédex Nacional) y ve su ficha completa (tipos, estadísticas, dónde y cómo se obtiene, movimientos) en pocos clics.

Este repositorio cubre el **backend**: una API REST estable que el frontend pueda consumir sin acceder directamente a la PokeAPI.

## Funcionalidades (ver `docs/specs/`)
| ID | Funcionalidad | Estado |
|---|---|---|
| F1 | Catálogo y ficha de Pokémon | Hecha |
| F2 | Filtros del catálogo | Hecha |
| F3 | Importación desde la PokeAPI | Hecha |
| F4 | Movimientos | Hecha |
| F5 | Rutas y regiones | Hecha |
| F6 | Nombres en español | Hecha |
| F7 | Cadena evolutiva | Hecha |
| F8 | Filtros combinados | Hecha |

## Fuera del MVP
- Autenticación y roles: login, registro de usuarios externos y favoritos por cuenta.
- Datos de captura que la PokeAPI no tiene: casi todos los Pokémon de Galar y Paldea quedan con `tipoObtencion = "Sin datos"` o sin rutas.
- Batallas simuladas o lógica de combate.
- Interfaz gráfica en el backend: este proyecto es solo una API REST para el frontend.
- Pokémon shiny, formas regionales y megaevoluciones como entradas separadas.
- Edición de Pokémon desde la API: los Pokémon solo entran por importación.

## Cómo sabremos que funciona
- Las 9 generaciones (1025 Pokémon) importadas y consultables desde el frontend, con 0 fallidos en la importación.
- Todos los criterios de aceptación de F1–F8 se cumplen (se convierten en pruebas en la U5).
- El frontend no necesita llamar a la PokeAPI para nada.
