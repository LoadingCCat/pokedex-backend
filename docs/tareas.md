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
- [x] B.28 F6 · Nombres de lugares en español desde los títulos verificados de WikiDex (sin pisar los renombrados con PUT). Reimportadas las 9 generaciones: 0 fallidos, 240 lugares pasan a español
- [ ] B.26 Pulir textos de `condicion` en las evoluciones (requiere reimportar): falta `min_steps` (Pawmot sale "Subiendo de nivel" en vez de caminar 1000 pasos), "Usando un movimiento, tras usar Puño Furia 20 veces" es redundante (Annihilape), Basculegion repite la condición por género, y las formas no regionales salen con el slug en inglés (Wormadam, Urshifu)

- [x] B.27 F4 · Campo `tipo` en `Movimiento` (importación, POST/PUT, contrato y pruebas). Reimportadas las 9 generaciones: 1025 importados, 0 fallidos; los 797 movimientos tienen `tipo`

## Front (U3)
<!-- Se detalla cuando el backend esté terminado. Pedir las tareas al agente en modo plan,
     a partir de docs/specs/ y docs/contratos/api.md. Antes: identidad visual en docs/diseno/
     y la skill ui-componentes. -->
- [x] FE.1 Identidad visual «Laboratorio» en `pokedex-frontend/docs/diseno/identidad-visual.md` (paleta AA, 18 colores de tipo, tipografía, componentes, tokens de Tailwind)
- [x] FE.2 Skill `ui-componentes` en `pokedex-frontend/.claude/skills/`
- [x] FE.3 Docs del front: `CLAUDE.md` y `docs/pantallas.md` (P1 Catálogo, P2 Ficha, P3 Movimientos, P4 Regiones, P5 Ruta)
- [x] FE.4 Base: tokens y fuentes de la identidad, `lang="es"`, imágenes remotas, cabecera y pie, `lib/formato.ts`, `lib/tipos-pokemon.ts`
- [x] FE.5 Volcado mock (`scripts/generar-mock.mjs` → `datos/mock/`) desde el backend local: 1025 Pokémon, 797 movimientos, 620 rutas, 540 cadenas evolutivas (unos 3,3 MB)
- [x] FE.6 Capa `lib/api`: tipos del contrato, mock en memoria que reproduce `api.md`, cliente HTTP con `API_URL`; 23 pruebas del mock (F1, F2, F4, F5, F7, F8, F9) y 26 que lo comparan con la API real
- [x] FE.7 Componentes base (`components/ui`, `InsigniaTipo`, `TarjetaPokemon`, `ImagenPokemon`)
- [x] FE.8 P1 Catálogo con buscador, filtros combinados, orden y paginación en la URL
- [x] FE.9 P2 Ficha: datos, estadísticas, obtención y rutas, cadena evolutiva, 404 propio
- [x] FE.10 Movimientos: tabla en la ficha y P3 `/movimientos`
- [x] FE.11 P4 Regiones y P5 Ruta
- [x] FE.12 Pulido: `error` global (sin `loading` global: cada pantalla tiene su esqueleto), metadata, repaso de accesibilidad y móvil, README
- [x] FE.13 Publicar en Vercel con el mock: <https://pokedex-frontend-seven.vercel.app/> (repo público `LoadingCCat/pokedex-frontend`, rama `master`; cada push despliega)

## Front · nostalgia, imágenes y WikiDex
<!-- Por fases; cada fase se aprueba antes de implementarla. Spec: pantallas.md, "Ampliación…" y P6. -->
- [x] FE.14 Fase 0 · Docs: "Toques de nostalgia" en la identidad, ampliación y P6 en `pantallas.md`, decisiones
- [x] FE.15 Fase 1 · Sprite animado de Negro/Blanco al pasar el ratón por las tarjetas (#001–#649)
- [x] FE.16 Fase 1 · Botón "Escuchar" con el grito en la ficha
- [x] FE.17 Fase 1 · Interruptor "Shiny" en la ficha
- [x] FE.18 Fase 1 · Barras de estadísticas que se llenan al aparecer
- [x] FE.19 Fase 2 · Mapas de las 9 regiones en P4 (`datos/imagenes.json`)
- [x] FE.20 Fase 2 · Capturas de lugares en P5, empezando por Kanto
- [x] FE.21 Fase 3 · Enlaces verificados a WikiDex (script + ficha, lugares y regiones)
- [x] FE.22 Fase 4 · `/creditos`, tamaño del build, capturas de referencia, evaluación y bitácora
- [x] FE.23 Fase 5 · Capturas del juego en la ficha: datos, sección y créditos, con la primera generación (Articuno, Zapdos, Moltres, Mewtwo y Mew)
- [x] FE.24 Fase 5 · Capturas del juego del resto de legendarios y singulares: 87 capturas de 52 de los 94 (los demás no tienen en WikiDex capturas de cómo se consiguen)
- [x] FE.25 Fase 6 · Disco de MT del color del tipo en las tablas de movimientos (requiere B.27 y regenerar el mock)
- [x] FE.26 Mejoras de la evaluación de la rúbrica (puntos 1–5, 7 y 8)
- [x] FE.27 Capturas de los lugares de Kanto que faltan: 46 más, 74 de 88 (los 14 restantes no tienen capturas útiles en WikiDex)
- [x] FE.28 Mock regenerado con los lugares en español y docs del front al día
- [x] FE.29 Capturas de la elección del inicial en la ficha de los 29 iniciales
- [x] FE.30 Captura del lugar donde aparece en las fichas sin capturas propias
- [x] FE.31 Capturas de los lugares de las demás regiones: 274 más, 348 de 620; 801 fichas con capturas
- [x] FE.32 Sprite clásico pixelado (normal y shiny) junto a la ilustración de la ficha

## Ampliación · requerimientos de la entrevista (F11–F15)
<!-- Aprobada el 2026-11-17 (Fase 0). Plan: PROMPT-ampliacion.md. Cada fase se aprueba antes de empezarla.
     Al final de cada fase: .\mvnw.cmd test y npm run test completos, con el total reportado (antes: 87 + 58 = 145).
     Antes de cualquier reimportación: pg_dump y avisar dónde quedó el respaldo. -->
- [x] Fase 0 · Specs F11–F15, contrato (sección "Ampliación"), `pantallas.md` (P7, P8), tareas y decisiones; medición en la PokeAPI (≈ 630 000 filas de aprendizaje y ≈ 60 000 encuentros para los 1025)

**Fase 1 · F14 Filtro por dos tipos**
- [x] B.29 F14 · `tipo` repetible (hasta 2, Y) en `GET /pokemons`; 3 o más → 400; 6 pruebas nuevas (93 en total)
- [x] FE.33 F14 · Selector "Tipo 2" en P1, chips, mock y pruebas (7 nuevas; 3 consultas más en la prueba de contrato)

**Fase 2 · F15 Autenticación y panel**
- [x] B.30 F15 · Spring Security + JWT (oauth2-resource-server), tabla `usuario` (BCrypt), administrador inicial desde `ADMIN_USERNAME`/`ADMIN_PASSWORD`, `JWT_SECRETO`, CORS; 401/403 con el formato de error; las pruebas de escritura existentes usan un usuario ADMIN simulado. 14 pruebas nuevas (107)
- [x] B.31 F15 · `DELETE /rutas/{id}` (desvincula Pokémon; los lugares importados quedan en `ruta_excluida` y no vuelven al reimportar). 6 pruebas nuevas (113)
- [x] B.32 F15 · `POST`/`PUT`/`DELETE /pokemons`, `editadoAMano`, `pokemon_excluido` y la reimportación que los respeta; errores con `campos`; colección de Postman en `docs/postman/` (login con `{{token}}` y crear, editar y borrar; 15 peticiones). 16 pruebas nuevas (129)
- [x] FE.34 F15 · `/login`, route handler de sesión (cookie `httpOnly`), `proxy.ts` y aviso en modo mock. 3 pruebas nuevas; el login de punta a punta queda por comprobar con el backend reiniciado
- [x] FE.35 F15 · `/admin` Pokémon (listado, búsqueda, crear, editar y eliminar con confirmación; validación y 400 por campo). 5 pruebas nuevas de la validación; la prueba de punta a punta con el backend queda al final de FE.37
- [x] FE.36 F15 · `/admin` Movimientos (listado con búsqueda, crear, editar y eliminar; sin precisión hasta B.35). 2 pruebas nuevas
- [x] FE.37 F15 · `/admin` Rutas (listado por región, porque la API no busca rutas por nombre; crear, editar y eliminar). 1 prueba nueva. Frontend: 69 pruebas (+29 de contrato con `API_URL`)

**Fase 3 · F11–F13 en el backend** — **siguiente fase** (después de la sustentación)
- [ ] B.33 F11 · `Habilidad`, `PokemonHabilidad`, importación y `GET /pokemons/{id}/habilidades`
- [ ] B.34 F13 · `Juego` (versiones y grupos) y `GET /juegos`
- [ ] B.35 F12 · `Aprendizaje` (`SEQUENCE`, `hibernate.jdbc.batch_size`, `order_inserts`, `order_updates`), `Movimiento.precision`, `/movimientos?juego=&metodo=` y la etiqueta
- [ ] B.36 F13 · `Encuentro`, `GET /pokemons/{id}/ubicaciones` y el filtro `juego`
- [ ] B.37 Respaldo con `pg_dump` y una sola reimportación de las 9 generaciones: 1025 importados, 0 fallidos, cuántas habilidades, encuentros y filas de aprendizaje, y **cuánto tardó**
- [ ] B.38 Quitar `pokemon_movimiento` (código y tabla, a mano, tras el respaldo)

**Fase 4 · F11–F13 en el frontend** — **siguiente fase** (después de la sustentación): depende de la Fase 3
- [ ] FE.38 Mock regenerado y partido por Pokémon (`datos/mock/pokemon/{id}.json`, carga bajo demanda) y `lib/api`
- [ ] FE.39 F11 · Habilidades en la ficha
- [ ] FE.40 F13 · Selector de juego en la URL (P1 y P2)
- [ ] FE.41 F12 · Tabla de movimientos con método, nivel, precisión, etiqueta y filtros
- [ ] FE.42 F13 · Ubicaciones por juego con probabilidad, método y niveles
- [ ] FE.43 Pruebas de contrato (mock contra la API real) ampliadas

**Antes de la sustentación · F19 Debilidades y naturalezas (datos fijos, sin reimportar)**
- [x] FE.46 F19 · Spec F19 en `docs/specs/` y la ficha y P11 `/naturalezas` en `pantallas.md`
- [x] FE.47 F19 · Debilidades y resistencias en la ficha, calculadas con la tabla fija de efectividad de tipos. 5 pruebas
- [x] FE.48 F19 · Página `/naturalezas` con las 25 naturalezas desde un archivo de datos fijo. 3 pruebas. Frontend: 77 pruebas (+29 de contrato con `API_URL`)

**Ampliación 2 · F16–F18 (`PROMPT-ampliacion-2.md`: formas alternativas, objetos y captura)** — **siguiente fase** (después de la sustentación)

**Fase 5 · Mediciones (RNF-01, RNF-03)**
- [x] FE.44 Lighthouse (móvil y escritorio) del catálogo, una búsqueda y una ficha: tiempo hasta interactiva y si queda por debajo de 2 s → `pokedex-frontend/docs/mediciones.md`: escritorio, catálogo 1,3 s y búsqueda 0,9 s (cumplen), ficha 2,1 s; móvil simulado 3,6–5,1 s (no cumple). Accesibilidad 100
- [x] FE.45 Playwright a 360, 768 y 1080 px (catálogo, ficha, regiones, `/login`, `/admin`): sin scroll horizontal; correcciones; `pokedex-frontend/docs/mediciones.md` → 55 de 55 sin scroll (también 375 y 1280); se corrigió la navegación, que se salía a 360 px con «Naturalezas». Falta `/admin` con datos (necesita el backend)

**Fase 6 · Cierre**
- [ ] B.39 `producto.md`, `arquitectura.md`, `pruebas.md` (totales), README y CLAUDE.md de los dos repos y `BITACORA_IA.md`; total de pruebas de las dos suites

## Siguiente fase · F11–F13 y F16–F18
<!-- Quedan como "siguiente fase" (después de la sustentación): no se tocan mientras dure el plan de abajo.
     Detalle arriba: Fase 3 y Fase 4 (F11–F13: B.33–B.38 y FE.38–FE.43) y "Ampliación 2" (F16–F18).
     Retomarlas solo cuando la F20 y la entrega (U6) estén cerradas; B.37 sigue siendo la única reimportación. -->

## Plan Claude Code · F20, interventoría y entrega (U4–U6)
<!-- Plan: PLAN_CLAUDE_CODE.md (raíz de Pokedex+). Una tarea a la vez, un commit por tarea, con aprobación antes del commit.
     Al terminar cada fase: .\mvnw.cmd test y npm run test, con los totales reportados (partida: 129 + 77). -->

**Fase 1 · Planos (solo docs)**
- [x] B.40 F20 · Decisiones, spec `F20-identificacion-pokemon.md` y contrato (`POST /pokemons/identificar`, `GET /health`, errores 422, 429 y 502)
- [x] B.41 Modelo de datos (`docs/datos/modelo.md`) y skill `backend-seguro` adaptada a Spring Boot, con su sección en `CLAUDE.md`
- [x] B.42 Tareas del plan en este archivo (B.40 en adelante y FE.49 en adelante); F11–F13 y F16–F18 como "siguiente fase"

**Fase 2 · Backend de F20**
- [x] B.43 Base para la nube: `GET /api/health`, `server.port=${PORT:8080}`, `forward-headers-strategy`, límites de `multipart` y `permitAll()` en `POST /api/pokemons/identificar`
- [x] B.44 Servicio de visión: `ServicioVision`, `ServicioVisionMock` (perfil `e2e` y pruebas) y `ServicioVisionGemini` (`GEMINI_API_KEY`, `GEMINI_MODELO`, timeouts 5 s / 20 s; sin clave → 502, sin fallar al arrancar)
- [x] B.45 Validación de la foto (presente, no vacía, máx. 5 MB, JPEG/PNG/WEBP por los primeros bytes) y límite de 10 por minuto por IP; pruebas de 400 y 429
- [x] B.46 Similares en PostgreSQL (tipo compartido con `EXISTS`, cercanía de total, misma generación; siempre 3); pruebas en `ApiConPostgresTest`
- [x] B.47 `IdentificacionService`, `POST /api/pokemons/identificar` y 422, 429 y 502 en `GlobalExceptionHandler`; una prueba de integración por criterio de F20

**Fase 4 · Interventoría (U5)**
- [ ] B.48 Auditoría en sesión limpia, sin modificar archivos → `docs/calidad/interventoria.md` (hallazgo | archivo:línea | severidad | cómo comprobarlo | corrección)
- [ ] B.49 Corrección de los hallazgos: causa raíz, prueba de regresión y commit `fix: … (closes #n)`; pendientes justificados (incl. `ddl-auto=update` frente a Flyway)
- [ ] B.50 Interventoría cruzada: hallazgos del otro equipo como issues con `.github/ISSUE_TEMPLATE/interventoria.md`

**Fase 5 · Entrega de llaves (U6)**
- [x] B.51 Docker: `Dockerfile` multi-stage (JDK 21 → JRE 21, usuario sin privilegios, `chmod +x mvnw`) y `.dockerignore`
- [ ] B.52 CI del backend (`.github/workflows/ci.yml`: Java 21, `./mvnw -B test` y `package`) y protección de `main` (PR + check `ci`) → `.github/workflows/ci.yml` (jobs `test` y `docker`). **Workflow listo y en uso; pendiente:** crear el ruleset de GitHub (`docs/despliegue.md` §8), sin exigir PR y con bypass de admin
- [x] B.53 Entornos y datos: Neon `dev` y `prod` (`unaccent`, `pg_dump`/`pg_restore`) y dos servicios Render con sus variables (solo nombres en los docs) — desplegado y verificado (Neon `production` y `dev`, Render prod y dev; URLs en `docs/despliegue.md`)
  - [ ] Prueba de `X-Forwarded-For` en Render (`docs/despliegue.md` §6.3): 11 envíos con la cabecera falsa → el 11.º debe dar 429; y 3 seguidos desde otra red → 200. Pendiente; si falla, ajustar `server.tomcat.remoteip.internal-proxies`
- [ ] B.54 Lanzamiento con rollback: rama → PR → CI → preview → merge → producción; simulacro de "Rollback" en Render — **pendiente:** simulacro de «Rollback» en Render (`docs/despliegue.md` §9)
- [ ] B.55 `docs/operacion/despliegue.md`, "Arquitectura desplegada" en `arquitectura.md`, README con la URL de producción y métricas del módulo en `BITACORA_IA.md` → `docs/despliegue.md` (con las URLs reales), «Arquitectura desplegada» y README con la URL de producción. **Pendiente solo:** métricas del módulo en `BITACORA_IA.md`

**Frontend (repo `pokedex-frontend`)**
- [x] FE.49 F20 · Capa de datos: tipos, `identificar()`, route handler `/api/identificar` hacia `API_URL` (tope de 2 MB; las server actions topan en 1 MB), espera de 70 s en las lecturas y en las escrituras del panel, y mock "no disponible" (503). El mensaje "despertando el servidor…" queda para FE.50. Frontend: 97 pruebas (+29 de contrato con `API_URL`)
- [x] FE.50 F20 · Pantalla `/identificar`: `SelectorFoto`, reducción a 1024 px en JPEG, estado de carga, tarjeta del identificado y 3 similares, mensajes 400/422/429/502, aviso "la foto no se guarda" y enlace en la cabecera; pruebas en Vitest. Reducción en el navegador con canvas (1024 px, JPEG, < 1,5 MB, orientación EXIF), mensaje «Despertando el servidor…» a los 5 s, cancelación al salir de la pantalla y `apiDisponible()`. Frontend: 140 pruebas (97 anteriores + 43; +29 de contrato con `API_URL`)
- [x] FE.51 Playwright: `playwright.config.ts`, script `test:e2e` y recorridos `catalogo`, `identificar`, `admin` y `responsive` en `e2e/*.e2e.ts` (8 pruebas; credenciales en `.env.test`, fuera de Git; backend con el perfil `e2e`). La suite `ficha` queda fuera: no estaba entre los recorridos pedidos. Ver `docs/pruebas.md`
- [ ] FE.52 CI del frontend (Node 22, `npm ci`, `lint`, `test`, `build`) y protección de `main` → `.github/workflows/ci.yml` (job `build`; sin E2E). **Workflow listo y en uso; pendiente:** ruleset de GitHub (`docs/despliegue.md` §8)
- [x] FE.53 Vercel: `API_URL` en Production (API de prod) y en Preview (API de dev). Verificar también la duración máxima de la función de `/api/identificar` (`maxDuration`): con Render dormido la llamada puede tardar cerca de 70 s y el límite del plan de Vercel podría cortarla antes — hecho: `maxDuration` 120 s, timeout 110 s, imágenes, 151 fichas y build con `API_URL`; `API_URL` en Production y Preview; identificación real con Gemini verificada en producción desde el celular
- [ ] FE.54 Simulacro de despliegue roto e Instant Rollback en Vercel — **pendiente:** simulacro de despliegue roto e Instant Rollback en Vercel (`docs/despliegue.md` §9)
- [x] FE.55 README del frontend con la URL de producción → README con las URLs de producción y de desarrollo

## Nube (U6)
<!-- Se detalla al iniciar la U6. El detalle de la entrega está en "Plan Claude Code · Fase 5" (B.51–B.55, FE.52–FE.55). -->
