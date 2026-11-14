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

## Back · pendiente
- [ ] B.25 Reimportar las 9 generaciones (`total=1025`) para llenar las cadenas evolutivas (hoy solo están las de los Pokémon 1–133)
- [ ] B.18 Decidir si al reimportar se actualizan `categoria` y `potencia` de los movimientos existentes (hoy solo `nombreEs`, para no pisar cambios hechos con PUT)
- [ ] B.22 Búsqueda por nombre sin importar tildes (extensión `unaccent` de PostgreSQL)
- [ ] B.19 Pruebas de los criterios de aceptación de F1–F5 sin depender de PostgreSQL (U5)
- [ ] B.20 Importación en segundo plano con endpoint de progreso (opcional)

## Front (U3)
<!-- Se detalla cuando el backend esté terminado. Pedir las tareas al agente en modo plan,
     a partir de docs/specs/ y docs/contratos/api.md. Antes: identidad visual en docs/diseno/
     y la skill ui-componentes. -->

## Nube (U6)
<!-- Se detalla al iniciar la U6. -->
