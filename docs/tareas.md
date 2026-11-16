# Tareas · Pokedex+

Una tarea a la vez: implementar → probar → revisar el diff → commit → marcar `[x]`.
Las tareas se piden al agente al empezar cada parte (back, front, nube) y se aprueban antes de implementarlas.

## Back · hecho
- [x] B.1 Proyecto Spring Boot + PostgreSQL, entidades y repositorios
- [x] B.2 F3 · Importación desde la PokeAPI (Pokémon, tipos, movimientos)
- [x] B.3 F1 · Listado paginado, búsqueda por nombre y ficha
- [x] B.4 F2 · Filtros por generación, tipo, región, ruta y forma de obtención
- [x] B.5 F5 · Rutas reales desde `/encounters` y regiones
- [x] B.6 F4 · CRUD de movimientos con validación
- [x] B.7 Manejo global de errores con formato uniforme
- [x] B.8 Credenciales fuera de git (`application-local.properties`)
- [x] B.9 Timeouts en la PokeAPI, bloqueo de importaciones simultáneas (409), `sort` inválido → 400, 500 sin detalles
- [x] B.10 Carga por lotes (N+1) y formato fijo de paginación
- [x] B.11 F1 · Estadísticas base · F4 · Movimientos por Pokémon
- [x] B.12 `CLAUDE.md` con el contexto del proyecto
- [x] B.13 F6 · Campo `nombreEs` en Pokemon y Movimiento
- [x] B.14 F6 · Búsquedas por `nombre` o `nombreEs`
- [x] B.21 Documentación SDD (`docs/`, bitácora, skill `nuevo-endpoint`)
- [x] B.23 Importar las 9 generaciones (`total=1025`): 1025 importados, 0 fallidos (620 rutas, 797 movimientos)
- [x] B.17 Paginación en `/movimientos` y `/rutas` (cambió el contrato: avisar al frontend) + `sort` inválido → 400 también en consultas `@Query`
- [x] B.16 F8 · Filtros combinados en `GET /pokemons` (`nombre`, `generacion`, `tipo`, `region`, `obtencion`)
- [x] B.24 F1 · Orden por defecto `idPokedex` en todas las listas de Pokémon (antes salían en el orden físico de la BD)
- [x] B.15 F7 · `GET /pokemons/{id}/evoluciones` (cadenas guardadas al importar, tabla `evolucion`)
- [x] B.25 Reimportar las 9 generaciones para llenar las cadenas evolutivas: 1025 importados, 0 fallidos; las 1025 etapas responden en `/evoluciones`, ninguna "Método especial" y una sola "Sin datos en la PokeAPI" (Manaphy, que la PokeAPI lista sin condición)
- [x] B.18 Decidir si al reimportar se actualizan `categoria` y `potencia` de los movimientos existentes (hoy solo `nombreEs`, para no pisar cambios hechos con PUT) → decidido: se deja así (ver `docs/decisiones.md`)
- [x] B.22 F9 · Búsqueda por nombre sin importar tildes (`unaccent` de PostgreSQL, creada al arrancar con `schema.sql`)
- [x] B.20 F10 · Importación en segundo plano (202) con progreso en `GET /api/pokemons/importacion`; sigue el 409 si ya hay una en curso
- [x] B.19 Pruebas automáticas de F1–F10 (80): controllers con `@WebMvcTest`, servicios con simulaciones y consultas SQL contra PostgreSQL embebido; sin PostgreSQL local ni PokeAPI (`docs/pruebas.md`)

## Back · pendiente
- [ ] B.26 Pulir textos de `condicion` en las evoluciones (requiere reimportar): falta `min_steps` (Pawmot sale "Subiendo de nivel" en vez de caminar 1000 pasos), "Usando un movimiento, tras usar Puño Furia 20 veces" es redundante (Annihilape), Basculegion repite la condición por género, y las formas no regionales salen con el slug en inglés (Wormadam, Urshifu)

## Front (U3)
<!-- Se detalla cuando el backend esté terminado. Pedir las tareas al agente en modo plan,
     a partir de docs/specs/ y docs/contratos/api.md. Antes: identidad visual en docs/diseno/
     y la skill ui-componentes. -->
- [x] FE.1 Identidad visual «Laboratorio» en `pokedex-frontend/docs/diseno/identidad-visual.md` (paleta AA, 18 colores de tipo, tipografía, componentes, tokens de Tailwind)
- [x] FE.2 Skill `ui-componentes` en `pokedex-frontend/.claude/skills/`
- [x] FE.3 Docs del front: `CLAUDE.md` y `docs/pantallas.md` (P1 Catálogo, P2 Ficha, P3 Movimientos, P4 Regiones, P5 Ruta)
- [x] FE.4 Base: tokens y fuentes de la identidad, `lang="es"`, imágenes remotas, cabecera y pie, `lib/formato.ts`, `lib/tipos-pokemon.ts`
- [ ] FE.5 Volcado mock (`scripts/generar-mock.mjs` → `datos/mock/`) desde el backend local con los 1025 importados
- [ ] FE.6 Capa `lib/api`: tipos del contrato, mock en memoria que reproduce `api.md`, cliente HTTP con `API_URL`; pruebas con vitest
- [ ] FE.7 Componentes base (`components/ui`, `InsigniaTipo`, `TarjetaPokemon`, `ImagenPokemon`)
- [ ] FE.8 P1 Catálogo con buscador, filtros combinados, orden y paginación en la URL
- [ ] FE.9 P2 Ficha: datos, estadísticas, obtención y rutas, cadena evolutiva, 404 propio
- [ ] FE.10 Movimientos: tabla en la ficha y P3 `/movimientos`
- [ ] FE.11 P4 Regiones y P5 Ruta
- [ ] FE.12 Pulido: `error`/`loading` globales, metadata, repaso de accesibilidad y móvil, README
- [ ] FE.13 Publicar en Vercel con el mock (el push y el despliegue los confirma el usuario)

## Nube (U6)
<!-- Se detalla al iniciar la U6. -->
