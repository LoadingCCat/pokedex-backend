# Bitácora de IA · Pokedex+ Backend

Registro de cómo se usó la IA: prompts clave, errores del agente y cómo se corrigieron.
Agregar una entrada por sesión de trabajo.

---

## Sesión anterior (chat aparte de Claude) · entre 2026-11-11 y 2026-11-14

Partió del backend inicial (`4056b5c`, 2026-11-11). Lo hecho en esta sesión se subió el 2026-11-14 en `d14b92e`, junto con las primeras correcciones de la sesión siguiente. Fuente: el resumen que dio ese chat al terminar (los prompts están parafraseados, no son citas).

### Prompts clave
- Revisar el backend en la carpeta del proyecto (Spring Boot + PostgreSQL + PokeAPI).
- Con un PDF de contexto técnico adjunto: completar el backend y arreglar los problemas encontrados.
- ¿Las rutas pueden salir de datos reales de la PokeAPI en lugar de ser inventadas?
- Explicar cómo se obtiene cada Pokémon que no tiene rutas (iniciales, legendarios, fósiles…).
- Cómo activar el acceso a red para que el agente compilara y ejecutara el código.

### Lo que hizo el agente
| Cambio | Archivos |
|---|---|
| Importador: descripción en español (con respaldo en inglés), generaciones 1–9, imagen oficial, altura y peso; movimientos duplicados revisados con una consulta a la BD | `PokemonService`, `MovimientoRepository` |
| Rutas reales desde `/pokemon/{id}/encounters`, guardadas una vez gracias al campo nuevo `idPokeapi`; las rutas de ejemplo se borran solas | `PokemonService`, `Ruta`, `RutaRepository` |
| Región de origen (`region`) y `/pokemons/region/{nombre}`: nativos de la región más los que aparecen en sus rutas | `Pokemon`, `PokemonRepository` |
| Forma de obtención (`tipoObtencion` + `formaObtencion`: Inicial, Singular, Fósil, Legendario, Regalo, Salvaje, Huevo, Evolución, Sin datos) y `/pokemons/obtencion/{tipo}`, con o sin tilde | `Pokemon`, `PokemonService`, `PokemonController` |
| Errores: `ResourceNotFoundException` (404) y `BadRequestException` (400); `GlobalExceptionHandler` también responde 400 y 405, todo con el mismo JSON | `exception/` |
| Endpoints nuevos: Pokémon `/ruta/{id}` y `/obtencion/{tipo}`; movimientos búsqueda, categoría, competitivos, `PUT` y `DELETE`; rutas `/regiones`, `/region/{r}` y `PUT`; validación en `POST` y `PUT` | `PokemonController`, `MovimientoController`, `RutaController` |
| La contraseña salió de `application.properties` a `application-local.properties` (en `.gitignore`), con un `.example` para el equipo | `application.properties`, `.gitignore` |
| README con endpoints, formato de errores y formas de obtención | `README.md` |

### Errores del agente y malentendidos
| Problema | Cómo se resolvió |
|---|---|
| El primer mensaje llegó cortado y el agente no sabía qué parte del proyecto le tocaba al usuario | El agente preguntó; el usuario aclaró que se encarga del backend |
| El agente no podía compilar: su entorno no tenía internet | Escribió el código revisando solo la sintaxis y el usuario compiló en su PC |
| Las primeras pruebas mostraban el comportamiento viejo | El servidor seguía corriendo con clases antiguas en `target/`: detenerlo y ejecutar `mvnw clean spring-boot:run` |
| Las rutas de ejemplo caían solo en 2 de las 6 de Kanto: la fórmula `(id*3+1) % 6` solo daba 1 o 4 | Se corrigió la fórmula y luego se reemplazó todo por rutas reales |
| Muchos Pokémon quedaban sin rutas (iniciales, Mew, casi todo Galar y Paldea) | Se agregaron `tipoObtencion` y `formaObtencion` para explicar cada caso |
| El build falló con "Could not find goal 'run.'" | El comando se había escrito `spring-boot:run.`, con un punto al final |
| Tras activar el acceso a red, el agente seguía sin internet | Ese ajuste solo se aplica a conversaciones nuevas: hubo que abrir otro chat |

---

## 2026-11-14 · Claude (chat con acceso a la carpeta del proyecto)

### Prompts clave
- *"Lee la estructura actual del proyecto… ¿Entendido? Dime qué necesitas que programemos o mejoremos."* → diagnóstico del backend con problemas priorizados (quedó en un Claude Doc).
- *"Sigamos el orden propuesto"* → 4 pasos: correcciones, push, rendimiento y paginación, y funcionalidades nuevas.
- *"Crea el CLAUDE.md"* → instrucciones del proyecto para Claude Code.
- *"Necesito hacer un SDD del proyecto… me faltaban features spec"* → la carpeta `docs/` con specs y criterios de aceptación.

### Lo que hizo el agente
| Cambio | Commit |
|---|---|
| Timeouts en la PokeAPI, 409 por importación en curso, `sort` inválido → 400, 500 sin detalles, `idPokeapi` protegido en `POST /rutas` | `d14b92e` |
| Carga por lotes contra el N+1 y formato fijo de paginación `{ content, page }` | `3d977a0` |
| Estadísticas base y `GET /pokemons/{id}/movimientos` | `5734778` |
| `CLAUDE.md` | `172b736` |
| Documentación SDD | (este commit) |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| Propuso `@EntityGraph` para el N+1, pero con dos `List` (`tipos`, `rutas`) Hibernate lanza `MultipleBagFetchException` y pagina en memoria | El mismo agente lo notó al implementar, antes de escribir código | Carga por lotes con `default_batch_fetch_size=50` |
| Al relacionar Pokémon con movimientos, `DELETE /movimientos/{id}` iba a fallar con 500 por la clave foránea | Revisión del diff antes de entregar | Desvincular de `pokemon_movimiento` antes de borrar |
| No podía compilar en su entorno (sin acceso a Maven Central) | Error 403 al compilar | El usuario compiló en su PC (`.\mvnw.cmd clean compile`); por eso se recomienda seguir en Claude Code |
| Dio comandos asumiendo la carpeta correcta; el usuario estaba en `pokedex+` y no en `pokedex-backend` | Error "mvnw.cmd no se reconoce" / "not a git repository" | Indicar el `cd` y abrir VS Code directamente en `pokedex-backend` |
| Dio varios comandos de CMD juntos, con comentarios `#`; al pegarlos se unieron (`git statusgit add .`) | Salida de la terminal | Dar los comandos de uno en uno y sin comentarios |

### Otros hallazgos
- El SDD inicial estaba desactualizado: describía un `id` autoincremental en `Pokemon`, pocos endpoints y CORS solo para `localhost:8080`.
- Había mucho trabajo local sin subir a GitHub (el remoto seguía en la versión del 11/nov).
- Los errores falsos de VS Code ("java.lang.Object cannot be resolved") venían de la extensión de Java, no del código: Maven compilaba bien.

### Verificación
Pikachu (`/api/pokemons/25`) con `totalEstadisticas = 320` y unos 110 movimientos en `/api/pokemons/25/movimientos`. Se revisó desde el navegador del usuario.

---

## 2026-11-14 · Claude Code (en la carpeta del proyecto)

### Prompt clave
*"Lee CLAUDE.md y README.md… Tarea: agregar nombres en español a Pokémon y movimientos usando la PokeAPI. No cambies el campo "nombre"… Antes de escribir código, muéstrame un plan corto y espera mi aprobación…"* (con requisitos, pasos de verificación y "no hagas push sin preguntarme").

### Lo que hizo el agente
| Cambio | Commit |
|---|---|
| F6 · `nombreEs` en Pokemon y Movimiento, búsqueda en ambos campos, reimportación de `nombreEs` en movimientos existentes, README y CLAUDE.md actualizados | `dbcfb78` |

| B.17 · Paginación en `/movimientos` y `/rutas`; `sort` inválido → 400 también en consultas `@Query`; B.23 verificada (1025 importados, 0 fallidos) | `68f8aa1` |
| B.16 · F8 filtros combinados en `GET /pokemons` (`PokemonFiltros` con `Specification`) | `af3898a` |
| B.24 · Orden por defecto `idPokedex` en las listas de Pokémon (hallado al probar F8: el catálogo empezaba en pikachu) | `ab7a2d4` |
| B.15 · F7 cadena evolutiva: tabla `evolucion` llenada al importar y `GET /pokemons/{id}/evoluciones` | (este commit) |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| El plan de F6 pedía verificar con `total=10`, pero Pikachu (25) queda fuera de ese rango | El agente lo avisó en el plan y lo confirmó al probar (`nombreEs` vacío) | Reimportar con `total=25` para la verificación |
| Regresión de F6 (`dbcfb78`): al pasar `/pokemons/buscar` de método derivado a `@Query`, `?sort=foo` empezó a responder 500 en vez de 400 (Hibernate lanza `UnknownPathException`, no `PropertyReferenceException`). En F6 no se probó el `sort` inválido | Al probar con `curl` los endpoints paginados de B.17 | `GlobalExceptionHandler` trata `UnknownPathException` como 400 cuando la petición trae `?sort=`. También corrigió `/pokemons/region` y `/pokemons/ruta`, que tenían el fallo desde antes |
| Para el orden por defecto iba a usar `@PageableDefault`, que fija el tamaño de página en 10 e ignora el 20 configurado | Revisión antes de escribir el código | `@SortDefault("nombre")` |
| En F7, la primera versión mezclaba las evoluciones de formas regionales con la normal (Slowbro: "Nivel 37 o Usando Brazal Galanuez") y le faltaban 3 métodos de evolución de la PokeAPI | Revisión con el advisor antes de cerrar la tarea, mirando la salida de las pruebas | Se usan `required_pokemon_form` / `evolved_pokemon_form` para descartar o aclarar las vías regionales, y se contrastó la lista con `/evolution-trigger` |

---

## 2026-11-14/15 · Claude Code (PC nueva)

### Prompts clave
- *"Acabo de pasar este proyecto a una PC nueva… ayúdame a dejarlo al día"* → revisar git contra `origin/main`, mover la skill a `.claude/skills/`, comprobar que `application-local.properties` no se sube, importar las 1025 y verificar B.25.
- *"Quiero dejar el backend completo antes de empezar el frontend. Trabaja UNA tarea a la vez… muéstrame un plan corto, espera mi aprobación…"* → 6 tareas en orden: docs al día, B.25, B.18 (decisión del usuario), B.22, B.20 y B.19 (con el enfoque aprobado antes).

### Lo que hizo el agente
| Cambio | Commit |
|---|---|
| Skill `nuevo-endpoint` movida a `.claude/skills/nuevo-endpoint/SKILL.md`; plantilla `application-local.properties.example` restaurada (se había perdido al copiar el proyecto) | `4db88cc` |
| Docs: criterios F1–F8 en `producto.md` y en B.19 (el resto ya estaba al día) | `945ed46` |
| B.25 confirmada sin cambios (1025 importados, 0 fallidos, Eevee con 8 ramas) | — |
| B.18 · Decisión del usuario: se deja como está (al reimportar, los movimientos solo actualizan `nombreEs`) | `29372b2` |
| B.22 · F9 búsqueda sin tildes con `unaccent` (`schema.sql` crea la extensión al arrancar) | `127a14c` |
| B.20 · F10 importación en segundo plano (202) con `GET /api/pokemons/importacion`; reimportación de las 1025 verificada (0 fallidos, 9,5 min) | `25e7f10` |
| B.19 · 80 pruebas automáticas de F1–F10: `@WebMvcTest`, servicios con simulaciones y `@SpringBootTest` contra PostgreSQL embebido (zonky); `docs/pruebas.md` | `33fc0ee` |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| Dijo que el servidor no mostraba en el log el avance de la importación; sí lo muestra cada 25 Pokémon | Al cerrar la terminal del servidor se vieron las líneas "Importando Pokémon... 850/1025" | Se corrigió en el plan de B.20 y ahora el avance también está en `GET /api/pokemons/importacion` |
| Arrancó el servidor desde su Bash: Tomcat falló con "Unable to establish loopback connection" (el Bash del agente no permite abrir puertos locales). Lo mismo pasaría con el PostgreSQL embebido de las pruebas | Al leer el log del arranque fallido | El servidor y las pruebas se lanzan en la terminal de la app; quedó anotado en `CLAUDE.md` y `docs/pruebas.md` |
| El plan aprobado de B.20 proponía una transacción por Pokémon (`TransactionTemplate`). Al implementarlo vio que, si una se deshacía, la caché de la importación quedaba con rutas y movimientos inexistentes y los Pokémon siguientes fallarían en cadena | Revisión del código antes de compilar | Se cambió la única lectura lazy (`pokemon.getRutas()`) por una consulta (`RutaRepository.buscarPorPokemon`); se avisó del cambio de plan y se registró en `decisiones.md` |
| El 202 de la importación salía con `fase: null` y `total: 0` (el hilo de fondo aún no había empezado) | Al probar con `curl` | `ProgresoImportacion.iniciar(total)` deja la fase `pokemons` y el total desde el principio |
| 7 de las 80 pruebas fallaron la primera vez: `jsonPath("$.content[*]…").value(List.of(…))` no funciona en Spring 7 para rutas con `[*]` (no era un error de la API) | Al correr `mvnw test` | Se usa el matcher `contains(…)` de Hamcrest |
| Al editar `CLAUDE.md` con `sed`, se perdió la barra invertida de `.\mvnw.cmd` | Revisión del resultado | Corregido con una edición exacta |

---

## 2026-11-16 · Claude Code (frontend, U3: FE.1–FE.13)

Sesión del frontend (`pokedex-frontend`, Next.js 16 + Tailwind 4), iniciada desde otra sesión de Claude Code. Las tareas, decisiones y esta bitácora se llevan en el backend.

**Publicado en Vercel:** <https://pokedex-frontend-seven.vercel.app/> · Repo: <https://github.com/LoadingCCat/pokedex-frontend>

### Prompts clave
- *"Propón 2 identidades visuales distintas… tono de voz, paleta HEX que cumpla contraste AA, tipografía de Google Fonts y estilo de componentes… Espera a que el usuario elija una"* → «Laboratorio» (clara, técnica) y «Dispositivo» (oscura, tipo consola), con el contraste de cada color medido. El usuario eligió **A · Laboratorio**.
- *"Sigue la planificación establecida"* → la skill `ui-componentes`, siguiente paso de `tareas.md`.
- *"Sí, planifica U3"* → plan en modo plan con dos preguntas: el front es **solo de consulta** (sin CRUD) y el mock es un **volcado completo** del backend. Plan aprobado: FE.3–FE.13.
- *"Sí, FE.n"* (una por tarea) → cada tarea con lint, build, pruebas, revisión en el navegador a 375 y 1280 px, commit y `[x]` en `tareas.md`.
- *"Hazlo público… ya puedes crear el repositorio"* y *"push de ambos"* → repo creado con `gh` y push solo tras el permiso explícito.
- *"Revisa los entregables de la U3 que faltan…"* → capturas de referencia evaluadas con la rúbrica, esta entrada y el enlace en el README.

### Lo que hizo el agente
| Cambio | Commit (front) |
|---|---|
| FE.1–FE.3 · Identidad «Laboratorio» (`docs/diseno/identidad-visual.md`, 18 colores de tipo AA), skill `ui-componentes`, `CLAUDE.md` y `docs/pantallas.md` (P1–P5) | `cfed966` |
| FE.4 · Tokens, fuentes, `lang="es"`, cabecera y pie, `lib/formato.ts`, `lib/tipos-pokemon.ts` | `bd5c1ac` |
| FE.5 · Volcado mock del backend: 1025 Pokémon, 797 movimientos, 620 rutas, 540 cadenas (`scripts/generar-mock.mjs`) | `648aada` |
| FE.6 · Capa `lib/api` (mock en memoria + cliente HTTP con `API_URL`); vitest: 23 pruebas del mock y 26 que lo comparan con la API real | `6c22f1c` |
| FE.7 · Componentes base | `79d27ac` |
| FE.8 · P1 Catálogo: búsqueda, filtros combinados, orden y paginación en la URL | `7325005` |
| FE.9 · P2 Ficha: estadísticas, obtención, cadena evolutiva, 404 propio; 1025 fichas estáticas | `91bf240` |
| FE.10 · Movimientos en la ficha y P3 `/movimientos` | `109f1fd` |
| FE.11 · P4 Regiones y P5 Ruta | `868faef` |
| FE.12 · `error.tsx`, README, repaso automático de accesibilidad y móvil | `e140e74` |
| FE.13 · Publicación en Vercel con el mock | `0312e55` |
| Capturas de referencia evaluadas con la rúbrica (`docs/diseno/referencias/`) | (este commit) |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| En la spec de P3 iba a combinar búsqueda, categoría y competitivos, pero la API tiene un endpoint por caso | Al leer el contrato antes de escribir la capa `lib/api` | Un solo selector de vista; el mock tampoco combina, para que funcione igual con la API real |
| Criterios de la spec escritos de memoria y no con los datos: Mew "sin rutas" (tiene Faraway Island) y la ruta "Bosque Verde" (se llama "Viridian Forest") | Al validar el volcado y al probar las pantallas | Criterios cambiados a Meganium y Viridian Forest |
| La plantilla de títulos "%s · Pokedex+" no se aplicaba al catálogo (página del mismo segmento que el layout) | Revisando `document.title` en el navegador | `title: { absolute: … }` en la página |
| Dos elementos hermanos con la misma `key` (la URL) en el catálogo | Error de React en la consola | Prefijos `filtros:` y `resultados:` |
| La ficha leía `params` fuera de `<Suspense>`, lo que impide la navegación instantánea con `cacheComponents` | Error de Next en la consola | Contenido de la ficha dentro de `<Suspense>` con esqueleto; anotado en `decisiones.md` |
| `/movimientos` salía ordenado por el slug en inglés ("Absorber", "Roca Veloz", "Ácido") | Al revisar la tabla en el navegador | `sort=nombreEs` (se comprobó que la API lo acepta en los 4 endpoints) |
| La tabla de movimientos tenía un ancho mínimo y obligaba a desplazarse en horizontal a 375 px | Captura en móvil | Sin ancho mínimo y con menos relleno en móvil |
| "Este Pokémon no aparece en estado salvaje" se repetía cuando `formaObtencion` ya lo decía | Al revisar Meganium | Solo se muestra si el texto no lo dice ya |
| Un `git add … public` falló porque la carpeta ya no existía, y el commit del backend salió antes que el del front | Salida del comando | Se repitió el commit del front sin esa ruta |
| La revisión automática de accesibilidad marcó "0 `h1`" en las fichas: medía antes de que React mostrara el contenido de `<Suspense>` | Al comparar con la página real | La revisión espera a que haya un `h1` visible; luego todo dio OK |
| Las primeras capturas a 390 px salieron cortadas: Chrome sin interfaz no deja una ventana tan estrecha y la página se pintaba a ~500 px | Al revisar las imágenes | Capturas con emulación de dispositivo (CDP, `setDeviceMetricsOverride`) |

### Otros hallazgos
- PostgreSQL estaba detenido y hacían falta permisos de administrador para iniciarlo (`net start` dio "Acceso denegado"); el usuario lo inició en una terminal de administrador.
- `gh` no estaba instalado; el usuario lo instaló con `winget` e inició sesión él mismo.
- Git y `gh` terminaron con código 255 aunque el push salió bien: se comprobó siempre con `git status` y `gh repo view`.
- La PokeAPI trae 18 lugares de Orre, una región que no está entre las 9 de origen: se muestran, pero su región va sin enlace.
- No se hicieron variantes /a /b /c de las pantallas (ver `decisiones.md`).

### Verificación
- 36 pruebas con vitest sin backend, y 62 con `API_URL` (las 26 de contrato comparan el mock con la API real). Build con 1034 páginas.
- Criterios de P1–P5 revisados en el navegador a 375 y 1280 px; revisión automática de accesibilidad de 13 URL.
- Web publicada: 14 URL comprobadas con `curl` (200 y título correcto; 404 propio en `/no-existe`; imágenes optimizadas).

---

## 2026-11-16 · Gemini (entorno en la torre nueva)

Configuración del backend en otro equipo (la torre) a partir del repositorio de GitHub. Fuente: el resumen que dio Gemini al terminar (prompts parafraseados). No hubo cambios de código ni commits.

### Prompts clave
- Continuar la sincronización y configuración de `pokedex-backend` en la torre a partir del repositorio de GitHub.
- Clonar el repositorio en `C:\Proyectos` y abrirlo en VS Code.
- Crear la base de datos local en pgAdmin 4 y el archivo de propiedades locales.
- Rellenar la sección "Sesión anterior" de esta bitácora (ver el último error).

### Lo que hizo el agente
- Estructura de carpetas en `C:\Proyectos\pokedex-backend`.
- Base de datos PostgreSQL `pokedex` creada en pgAdmin 4.
- `application-local.properties` con las credenciales y la URL de esa base de datos, fuera de git.

### Errores del agente y cómo se corrigieron
| Problema | Corrección |
|---|---|
| Los comandos `cd` y `mkdir` se pegaron juntos en PowerShell | Separarlos y ejecutarlos de uno en uno |
| La terminal de VS Code no reconocía `git` porque se abrió antes de que se registraran las variables de entorno | Clonar con el botón de VS Code o usar Git Bash |
| `application.properties` apunta a `pokedex_db` por defecto, pero la base se creó como `pokedex` | URL explícita en `application-local.properties` |
| Intentó rellenar "Sesión anterior" con suposiciones que contradecían el historial: la entidad `Evolucion`, el 409 y la carga por lotes son del 2026-11-14, y el importador nunca usó DTOs | Se descartó su versión; la sección se completó con el resumen del chat de Claude de esa sesión (`0d0c892`) |

---

## 2026-11-16 · Claude Code (frontend, nostalgia: FE.14–FE.22)

Continuación de la sesión del frontend. Ampliación por fases pedida por el usuario: en cada fase, primero los docs, después el plan, la aprobación y un commit por cambio, con lint, build, pruebas y revisión a 390 y 1280 px.

### Prompts clave
- *"Toques de nostalgia… por fases… Pregúntame antes de cualquier push. Es un proyecto universitario sin fines de lucro"* → Fase 0 (reglas en la identidad: un efecto destacado por pantalla, menos de 300 ms, nada con `prefers-reduced-motion`, sin 3D real, las imágenes nunca tapan el dato) y Fases 1–4.
- *"Apruebo, sigue"* (una por fase) → implementación de lo planeado; *"Sí, push de ambos"* al final de cada fase.
- Fase 1: sprites animados de Negro/Blanco al pasar el ratón, botón Escuchar (gritos de `PokeAPI/cries`), interruptor Shiny y barras que se llenan. La inclinación 3D con CSS se recomendó descartar (rompe la regla de un efecto por pantalla).
- Fase 2: mapas de las 9 regiones y capturas de lugares, empezando por Kanto, desde WikiDex/Bulbagarden revisando su política.
- Fase 3: "Ver en WikiDex ↗" verificado con un script que respete la wiki; lo no verificado, a la búsqueda.
- Fase 4: `/creditos`, tamaño del build sin backend, capturas de referencia, evaluación y esta entrada.

### Lo que hizo el agente
| Cambio | Commit (front) |
|---|---|
| FE.14 · Docs: "Toques de nostalgia" en la identidad, ampliación y P6 en `pantallas.md` | `e7aff43` |
| FE.15 · Sprite animado (#1–#649) al pasar el ratón por las tarjetas | `c3bfe21` |
| FE.16 · Botón Escuchar con el grito, solo al pulsarlo | `ac5eb06` |
| FE.17 · Interruptor Shiny (cambia la ilustración y su `alt`) | `de16e6b` |
| FE.18 · Barras de estadísticas que se llenan en 280 ms al aparecer | `708ab9e` |
| `sharp` (devDependency) y `scripts/imagenes.mjs`: 9 mapas y 28 capturas de Kanto de WikiDex en WebP (3,7 MB) con su fuente en `datos/imagenes.json` | `fe257e0`, `610c3cb` |
| FE.19 · Mapas en `/regiones` y en cada región | `28dbd40` |
| FE.20 · Capturas en `/rutas/[id]` (al lado en escritorio, debajo en móvil, panorámicas a todo el ancho) | `6b854f1` |
| FE.21 · `scripts/enlaces-wikidex.mjs` (253 peticiones, una por segundo): Pokémon 1025/1025, lugares 529 exactos y 91 de búsqueda, regiones 9/9 | `11cc4f1` |
| FE.21 · Componente `EnlaceExterno` y enlaces en la ficha, los lugares y las regiones | `67f0eea`, `a5da7c0` |
| FE.22 · `/creditos` (37 imágenes, recursos de la PokeAPI, WikiDex y aviso de Nintendo) y enlace en el pie | `c944b63`, `63460c9` |
| FE.22 · Capturas de referencia rehechas y evaluación con la rúbrica | `61201e4` |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| Al editar `tareas.md` con `Set-Content -Encoding utf8` de PowerShell, el archivo entero se reescribió con BOM | En el `git diff`, todo el archivo aparecía cambiado | Se recuperó la versión anterior con `git show` y la edición se hizo con `sed`; desde entonces, ediciones de docs con `sed` |
| Bulbapedia devolvió un desafío anti-bots de Cloudflare a las peticiones automáticas | La respuesta no era JSON | Se paró sin intentar saltarlo y se usó WikiDex (anotado en `decisiones.md`); el script de enlaces también se detiene ante algo que no sea JSON |
| El WebP con pérdida pesaba más que el original en mapas de pixel art | Comparando tamaños al generar | El script guarda el menor entre WebP con pérdida (calidad 80) y sin pérdida |
| En el prototipo de enlaces, los títulos de la segunda ronda se compartían entre lugares de la misma región: "Lake Valor" acababa en "Templo Puntaneva" | Revisando ejemplos al azar del resultado | Cada lugar solo prueba los títulos que salen de sus propios candidatos |
| Las rutas de Johto quedaban en búsqueda (WikiDex las titula "Ruta 29", sin región) y las Islas Sete no tenían categoría de región | Revisando la lista de lugares en búsqueda | Se prueba también "Ruta N" y se acepta la categoría "Archi7" para Kanto |
| Las capturas de 390 px salían recortadas: Chrome sin interfaz tiene un ancho mínimo de ventana | Al ver las imágenes | Emulación de dispositivo por CDP (`setDeviceMetricsOverride`) |
| La revisión automática de accesibilidad decía "0 h1": medía antes de que el contenido de `Suspense` apareciera | El `h1` sí estaba al mirar la página | Esperar a que haya un `h1` visible antes de medir |
| La prueba de los enlaces importaba el JSON con el alias `@/`, que vitest no resuelve | Al correr `npm run test` | Import relativo |
| Al rehacer las capturas desde el build local, el script se quedó esperando imágenes perezosas que nunca empezaban a cargar | Sin avance durante varios minutos | Imágenes forzadas a `eager` y un límite de 15 s de espera |

### Otros hallazgos
- El navegador integrado de la app hereda el `prefers-reduced-motion` de Windows del usuario, así que ahí los efectos están apagados (correcto). Se probaron con Chrome sin interfaz y la emulación de CDP.
- WikiDex no da autor por archivo: `/creditos` enlaza a la página de cada archivo original en lugar de nombrar autores.
- 91 lugares quedan con enlace de búsqueda; 18 son de Orre, que casi no tiene artículos.
- Durante una compilación, la CPU estaba al 91 % por un `javaw` ajeno: los tiempos de build de esa sesión no son comparables.

### Verificación
- Vitest: 45 pruebas sin backend (las 26 de contrato se saltan sin `API_URL`). Build sin backend con 1035 páginas.
- Tamaño del build sin backend, FE.13 → FE.22: estáticos 1071 → 1115 KB, JS del cliente 643 → 683 KB, servidor 382 → 389 MB, `public/` 0 → 3,8 MB (detalle en `decisiones.md`). El archivo de enlaces a WikiDex no llega al navegador.
- Todos los criterios de la ampliación y de P6 en `pantallas.md`, revisados a 390 y 1280 px; el nombre accesible de cada enlace externo, comprobado en el navegador.

---

## 2026-11-17 · Claude Code (frontend y backend: FE.23–FE.25 y B.27)

Continuación de la sesión anterior, con el mismo método por fases (docs → plan → aprobación → un commit por cambio, con lint, build, pruebas y revisión a 390 y 1280 px).

### Prompts clave
- *"A esto me refiero con capturas del juego en la página"* (con una galería de WikiDex: "Capturar a Mew" en la Isla Suprema) y *"En la ficha"* → Fase 5: sección "Cómo se consigue en el juego" en la ficha de legendarios y singulares.
- *"Los movimientos en el juego son en un disco, podrías agregarlo al apartado de movimientos"* y *"Por tipo (con backend)"* → Fase 6: campo `tipo` en `Movimiento` (cambia el contrato) y un disco de MT del color del tipo en las tablas.
- *"Sí, FE.24"* → el resto de legendarios y singulares.

### Lo que hizo el agente
| Cambio | Commit |
|---|---|
| FE.23 · Capturas del juego de Articuno, Zapdos, Moltres, Mewtwo y Mew (`scripts/imagenes.mjs` con apartado `pokemon`); pixel art de GB/GBA/DS a 2× pixelado, sin pérdida | front `a991708` |
| FE.23 · Sección en la ficha (dos por fila) y grupo "Capturas de Pokémon" en `/creditos` | front `c2c5653`, `ef39c28` |
| B.27 · `Movimiento.tipo` (importación, POST/PUT con validación de los 18 tipos, reimportación que solo llena los `null`), 5 pruebas nuevas, contrato y spec F4 | back `cfdecff`, `1cad1c3` |
| B.27 · Reimportación de las 9 generaciones: 1025 importados, 0 fallidos; 797 movimientos con `tipo` | back `1b8f1a9` |
| FE.25 · Mock regenerado con `tipo`; disco de MT (SVG propio) y columna Tipo en las tablas | front `f96fddc`, `0b62f66` |
| FE.24 · 87 capturas de 52 de los 94 legendarios y singulares; las de 3DS a su tamaño | front `6d6b85d` |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| El filtro de spin-offs (`/GO/i`) descartaba "Heart**Go**ld", y al arreglarlo con `sed` se perdieron las `\b` del regex | Faltaban capturas de HGSS evidentes ("Batalla contra Articuno HGSS") y luego salían spin-offs | Script nuevo con una lista de juegos de la serie principal en lugar de una de exclusión |
| Los reemplazos de varias líneas en Java no se aplicaron (los archivos tienen CRLF) y uno dejó el controller con finales mezclados | Al buscar los cambios con `grep` y con `file` | `git checkout` de los tres archivos y un script que normaliza a LF, reemplaza y vuelve a CRLF; la herramienta Edit dejó el controller en LF y se le devolvió el CRLF |
| La tabla de movimientos desbordaba a 390 px (45 px en la ficha) al añadir el disco y la columna Tipo | Medición de `scrollWidth` con Chrome sin interfaz | En móvil, Tipo y Competitivo van en texto bajo el nombre y el disco baja a 16 px |
| Las capturas de 3DS (400 px) se ampliaban a 512, en contra de la spec | Revisando los tamaños medidos de Latias | `Math.min(ancho, 512)` para las que no son pixel art |
| Pies de foto con nombres sin comprobar: "Parque Ramanas" (en español es Parque Hansa) y "templo de la Corona Nívea" | Consulta a la API de WikiDex antes de generar los datos | Corregidos; los pies se basan en el artículo donde WikiDex usa cada imagen |
| El servidor de desarrollo seguía mostrando el mock anterior ("Sin tipo") tras regenerarlo | Revisión en el navegador | Reinicio del servidor de desarrollo (el mock se cachea en memoria) |

### Otros hallazgos
- La sección de captura del ejemplo del usuario está en el artículo del lugar, no del Pokémon; en una muestra de 8 lugares de legendarios solo 1 la tenía. Las categorías de WikiDex (una por Pokémon + "Capturas de <juego>") cubren mucho más, pero mezclan spin-offs, demostraciones de movimientos y escaneos: se eligió a mano.
- 42 de los 94 legendarios y singulares no tienen en WikiDex capturas de cómo se consiguen; su ficha no muestra la sección.
- Con la reimportación cambió el peso de Dipplin (#1011) de 9,7 a 4,4 kg: lo corrigió la PokeAPI.
- Un Minecraft abierto (7,4 GB) dejaba la CPU al 100 %: pruebas y builds muy lentos. No se tocó.
- El panel del navegador de la app, oculto, deja las páginas a medio revelar (el contenido de `Suspense` queda en un `div` oculto): la verificación se hizo con Chrome sin interfaz por CDP.

### Verificación
- Backend: 83 pruebas; criterios de B.27 con `curl.exe` (`"FIRE"` → `fire`, `"luz"` → 400, PUT sin `tipo` lo conserva).
- Frontend: 46 pruebas sin backend y 72 con `API_URL` (las 26 de contrato contra la API real); build sin backend con 1035 páginas; `public/` en 6,7 MB.
- Criterios de las Fases 5 y 6 en `pantallas.md`, revisados a 390 y 1280 px.

### Continuación: FE.26 y FE.27 (2026-11-17)
- *"Bitácora y mejoras"* y *"y termina los lugares que faltan"* → FE.26 (mejoras 1–5, 7 y 8 de la evaluación) y FE.27 (46 lugares más de Kanto, 74 de 88).
- Commits del front: `f4c5822` (ficha y cadena evolutiva), `c3652a0` (filtros en móvil), `385f62b` (enlaces y mapa), `712c8c5` y `6b3348d` (lugares).

| Problema | Cómo se detectó | Corrección |
|---|---|---|
| Con 224 px, las condiciones de Leafeon y Sylveon seguían en 4–5 líneas | Midiendo líneas con Chrome sin interfaz | Columna de 288 px en tableta y 384 en escritorio: 2 y 3 líneas |
| El botón "Filtros (n)" también salía en escritorio: el `inline-flex` del botón base ganaba a `hidden` | Medición en 1280 px | Variantes (`md:hidden`), que se generan después de las utilidades base |
| `sed` sin nombre de archivo en un bucle (no marcó los criterios) | El propio `sed` avisó "no input files" | Se repitió con el archivo |
| Una variable `original` del script de imágenes chocaba con otra ya declarada | `node --check` | Renombrada |
| 9 capturas de pixel art (8 de la Fase 5) se habían guardado con pérdida | Leyendo la cabecera de cada WebP (`VP8` frente a `VP8L`) | El pixel art va siempre sin pérdida |
| El servidor de desarrollo no recogió el cambio de `FiguraCreditada` y servía la versión anterior | Mirando el HTML con `curl` | Reinicio del servidor de desarrollo |
| Las imágenes medían 2 px menos que su escala (478 en vez de 480) por el borde del bloque | Medición del `img` | La columna suma los 2 px del borde |

### Continuación: B.28, FE.28–FE.32 (2026-11-17)
- *"Lugares en español"* y *"Apruebo, España"* → B.28: el importador usa el título verificado de WikiDex cuando la PokeAPI no trae el nombre en español (`lugares-es.properties`, generado por `scripts/nombres-lugares-es.mjs` del front); 240 lugares pasan a español. FE.28 regenera el mock.
- *"Podrías poner las imágenes en el inicial… y para el resto una captura de ahí, o un avistamiento"* y *"Intenta llenar todos los que puedas"* → FE.29 (capturas de la elección del inicial) y FE.30 (captura del lugar donde aparece); FE.31, capturas de lugares de las demás regiones.
- *"Agrega también el pixelado clásico a un lado"* → FE.32: sprite clásico de 96 px a 2× junto a la ilustración, normal y shiny (los 1025 lo tienen).
- Commits: back `19627ac` (B.28); front `3bb76c9`, `b66ff30` (FE.28), `ee4ffda` (FE.30), `8fe442a` (FE.29), y FE.32.

| Problema | Cómo se detectó | Corrección |
|---|---|---|
| "Altar del Sol/de la Luna" y "Lago Corosol/Coroluna" no son pares Hispanoamérica/España sino de versión, y daban "de la Luna" | Revisando los 83 títulos con barra | Se excluyen en el script; esos lugares conservan su nombre de la PokeAPI |
| Las expresiones regulares del script se escribieron con barras de escape perdidas por el heredoc de Bash | `node` dio un error de sintaxis | Corregidas con la herramienta Edit |
| Los imports nuevos de `PokemonService` no se añadieron (el `sed` buscaba `\r` y el archivo había quedado con LF) | Error de compilación | Añadidos con Edit |
| Una prueba de FE.30 dependía de los nombres del mock, todavía en inglés | Falló antes de regenerar el mock | La prueba comprueba región y tipo de captura, no un nombre |
| Los guiones de comprobación con regex o `\n` dentro de `node -e` fallaban por el escapado | Error "Invalid regular expression" en Chrome sin interfaz | Se escriben como archivo `.js` |
| La captura de doble pantalla de Hoenn (400 × 480) se recortaba a 384 px | Medición del `img` | El recorte solo se aplica al pixel art de lugares |
| El primer intento de consulta de candidatas de iniciales tardó más de 10 min (Pikachu y Eevee tienen miles de imágenes en WikiDex) | El comando pasó a segundo plano | Se esperó; no se repitió |
- FE.31 (front `f60b378`): 274 lugares más de las otras regiones, elegidos con un filtro por nombre de archivo y revisados en hojas de contacto; 801 fichas con capturas. Errores corregidos: la primera selección incluía capturas con Pokémon, objetos ocultos, diálogos y mapas montados de 3DS (se endureció el filtro dos veces y se quitaron 10 a mano), y el pixel art de origen JPG se inflaba al guardarlo sin pérdida (Ruta 23 de Teselia: 3,5 MB → 613 KB). Un `sed` con `ó` en la expresión falló ("unterminated address regex") y se editó con Node.

### Ampliación F11–F15 (2026-11-17)
- *"Lee PROMPT-ampliacion.md y empieza por la Fase 0"* → medición en la PokeAPI (≈ 630 000 filas de aprendizaje, ≈ 60 000 encuentros), specs F11–F15, contrato, pantallas, tareas y decisiones. Commits: back `20332b5`, front `cccca29`.
- *"Aprobado"* (Fase 1) → B.29 y FE.33, filtro por dos tipos. Commits: back `584ad67`, front `6120a09`.
- *"Apruebo"* (Fase 2) → B.30, Spring Security + JWT.

| Problema | Cómo se detectó | Corrección |
|---|---|---|
| **Se mostró una línea de `application-local.properties` (la contraseña de PostgreSQL)**: un `grep` para buscar credenciales filtradas recorrió todo `src/` sin excluir ese archivo | La salida del propio `grep` | Se avisó al usuario y se le recomendó cambiar la contraseña. El archivo sigue fuera de git. Regla nueva en `CLAUDE.md`: toda búsqueda sobre `src/` excluye ese archivo |
| El `awk` que quitó la clase auxiliar de roles de `SeguridadConfig` se llevó también la llave de cierre de la clase | Revisando el final del archivo antes de compilar | Se repuso la llave |
| `next start` en el puerto 3100 falló (`EADDRINUSE`): lo ocupaba otro proceso, y Playwright cargó esa página | La notificación del proceso en segundo plano | Se usó el 3217; el proceso ajeno no se tocó |
| No hay Python en el equipo, y un heredoc de Bash con comillas y acentos graves falló | Errores de la terminal | Se editó con Edit y con scripts de Node en archivo |

---

## 2026-11-21 · Claude Code (F20 y Fase 5: identificación por foto y despliegue) · Métricas del módulo

Cierre de B.55. Todo lo que sigue sale de `git log`, de los informes de pruebas y de mediciones hechas con `next build`; los datos que solo tiene el equipo (tiempos, uso, Gemini y arranque en frío) los aportó el usuario. Los dos repos se trabajaron el mismo día (2026-11-21).

### Commits y tiempos (de `git log`)
| | Backend (`main`) | Frontend (`master`) |
|---|---|---|
| Primer commit de F20 | `993d3b2` · 12:16 (spec, contrato y decisiones) | `c3b22d0` · 14:16 (capa de datos) |
| Último commit de la Fase 5 | `228880b` · 22:05 (X-Forwarded-For verificado) | `a63f7b9` · 21:40 (URLs de producción) |
| Commits desde F20 hasta hoy | 21 (+1 de esta entrada) | 10 |
| Cambio acumulado | 51 archivos, +3 045 / −16 líneas | 49 archivos, +1 941 / −132 líneas |

- Por tramos: F20 backend de 12:16 a 15:32 (B.40–B.47); F20 frontend de 14:16 a 15:35 (FE.49–FE.50); pruebas E2E a las 19:53 (FE.51); Fase 5 de 20:15 a 22:05 (Docker, CI, configuración de producción, imágenes, `generateStaticParams`, documentación y cierre).
- Tiempo entre el primer y el último commit: unas 10 h de reloj. **Jornada real (dato del usuario):** de mediodía a las 10 p. m., unas 10 h; el commit de las 12:16 es de la mañana y corresponde a las tareas del backend de F20. Unas 10 sesiones de Claude Code.
- **Sesión de la Fase 5 (la de esta entrada):** 2 h 12 min de reloj, 14 min 42 s de API, 116 peticiones, 443 líneas cambiadas y un costo equivalente de 5,03 USD (Sonnet 5.5).

### Tareas cerradas
- 20 tareas `[x]` en el plan de F20 y entrega, tras cerrar B.55: B.40–B.47 (8, F20 en el backend), B.51–B.55 (5, Docker, CI, entornos, rollback y documentación) y FE.49–FE.55 (7, F20 en el frontend, E2E, CI y despliegue).
- Pendientes del plan (no son de la Fase 5): B.48–B.50 (interventoría).

### Pruebas, antes y después
| Suite | Partida (antes de F20) | Ahora | Cambio |
|---|---|---|---|
| Backend (`mvnw test`, JUnit) | 129 | **210** (0 fallos, informes de Surefire del 2026-11-21) | +81 |
| Frontend unitarias (Vitest, sin `API_URL`) | 77 | **141** | +64 |
| Frontend de contrato (Vitest con `API_URL`) | 29 | 29 (se saltan sin `API_URL`) | 0 |
| E2E (Playwright, Chromium) | 0 | **8** en 4 archivos (`admin`, `catalogo`, `identificar`, `responsive`) | +8 |

- Los E2E no corren en el CI (necesitan el backend con perfil `e2e` y la base con los 1025 Pokémon); se lanzan a mano con `npm run test:e2e`.
- El `CLAUDE.md` del backend todavía dice «107 pruebas»: se actualiza en B.39 (cierre).
- Corrección del 2026-11-22 (tarea 4.2): `mvnw test` sobre el commit `6dd6154` (antes de las correcciones) dio **204** pruebas, 0 fallos, no 210; no se pudo reproducir la cifra anterior. Tras la tarea 4.2 son **244**. `CLAUDE.md` ya dice 244.
- Actualización del 2026-11-22 (tarea 4.3, X-01): tras el grupo 5 y las correcciones de la 4.3, los totales medidos son backend 290 (`mvnw test`), frontend 233 de Vitest + 29 de contrato con `API_URL`, 19 E2E (`playwright test --list`); los 244 y 157 de la línea anterior eran los de justo después de la 4.2.

### Despliegue y peso
| Medida | Antes | Después |
|---|---|---|
| `.next/server/app/pokemon` (build en modo mock) | **843 MB**, 1025 fichas prerenderizadas | **151 MB**, solo las 151 de Kanto (el resto, en la primera visita) |
| Mismo directorio con `API_URL` (build contra la API) | no compilaba | 7 MB (solo esqueletos) |
| Transformaciones de imagen en Vercel | 1,4 K de 5 K al mes (dato del panel) | tope teórico ≈ 3 100 (1025 artworks × 3 anchos × 1 formato) |
| Almacenamiento de despliegues en Vercel | 7,51 de 10 GB (dato del panel) | no medido tras la limpieza de despliegues viejos |
| Imagen Docker del backend | no existía | 403 MB (JAR de 56,8 MB), usuario sin privilegios |
| CI | ninguno | GitHub Actions: backend (`test` + `docker`), frontend (`build`); rulesets activos |
| Funciones de identificación | sin límite declarado | `maxDuration` 120 s, fetch a 110 s |

- Entornos: Vercel (iad1), Render prod y dev (Virginia) y Neon `production` y `dev` (us-east-1), todos en US East.
- Identificación real con Gemini verificada en producción desde el celular. 
- **Gemini** (Google AI Studio, nivel gratuito, últimos 28 días): unas 12 solicitudes, 100 % exitosas y 0 errores, ≈ 15 K tokens de entrada y ≈ 370 de salida, modelo Gemini 3.5 Flash Lite: la cuota gratuita sobra.
- **Arranque en frío de Render (según los logs):** 116 y 134 s en dev, 122 s en prod; **más que el «hasta un minuto» de los docs y que el tope de 110 s del fetch de identificación** (ver «Hallazgo abierto» abajo).
- **Latencia de una identificación:** no se midió con cronómetro; con el servidor despierto, unos segundos.

### Incidentes encontrados y corregidos
| Incidente | Cómo se detectó | Corrección |
|---|---|---|
| **Hidratación en el celular:** al abrir `npm run dev` desde la IP del ordenador, la página se veía pero los botones no hacían nada | Next 16 bloquea `/_next/*` (JS y HMR) desde cualquier host que no sea `localhost` (en el log, `Blocked cross-origin request to Next.js dev resource`) | Variable `DEV_ORIGINS` → `allowedDevOrigins` (solo desarrollo) y `SelectorFoto` con `<label htmlFor>` a un input file enfocable, para que también abra el selector en iOS Safari. Front `c04ad3c` |
| **`next build` con `API_URL` fallaba** en `/creditos` y `/regiones`: el `fetch` a la API estaba fuera de `<Suspense>` | Al medir la tarea 5.4b contra el backend local: falla aunque la API esté arriba. No se había notado porque Vercel corría con el mock | Datos dentro de `<Suspense>` con esqueleto; compila con el backend arriba y contra un puerto cerrado, así que el build no depende de Render. Regla nueva en `CLAUDE.md`. Front `7c5816d` |
| **Contraseña del administrador:** el `pg_dump` trajo a Neon el admin local con la contraseña local | Al revisar `AdministradorInicial` (nunca pisa a un admin existente) frente al dump | Se documentó borrar la fila antes del primer arranque en producción y dejar que el servicio la cree con `ADMIN_USERNAME` y `ADMIN_PASSWORD` de Render (`docs/despliegue.md` §2). Sin cambiar código: un `ADMIN_RESTABLECER` se descartó. **Ejecutado:** el `DELETE` se hizo en production a las 9:10 p. m., antes del primer arranque a las 9:12, y en dev antes de crear su servicio; los dos logs dicen «Administrador inicial admin creado» |
| **Pool de Neon:** el pool propuesto dejaba 1 conexión mínima abierta, con lo que Neon no suspendería el cómputo y se gastarían las horas gratuitas | Observación del usuario al revisar el diff | `DB_POOL_MIN` por defecto en 0, con `idle-timeout` de 2 min y `max-lifetime` de 4 min (`e3949e4`) |
| **Almacenamiento de Vercel** (7,51 GB): 1025 fichas prerenderizadas, unos 820 KB cada una, en cada despliegue | Medición del output de `next build` | `generateStaticParams` con las 151 de Kanto (`7fa154b`). Falta limpiar despliegues viejos en el panel |
| **IP del límite de identificaciones:** duda de si Render deja el `X-Forwarded-For` falsificable o hace compartir cupo | Decisión pendiente desde F20 (2.3) | Verificado en producción: 10 envíos con cabecera falsa dieron 400, el 11.º 429; con la PC bloqueada, el celular con datos móviles identificó con 200 (`228880b`) |

### Errores del agente y cómo se corrigieron
| Problema | Cómo se detectó | Corrección |
|---|---|---|
| Propuse un `.vercelignore` para bajar el almacenamiento; solo aplica a despliegues por CLI y el proyecto usa Git | Al repasar la documentación antes de commitear | Se quitó antes del commit y se explicó la causa real (las fichas prerenderizadas) |
| Un `sed` con un patrón que no coincidía dejó `DB_POOL_MIN=1` en `CLAUDE.md` tras pasar a 0 | `grep` del resultado (0 coincidencias de `DB_POOL_MIN=0`) | Corregido y añadido al mismo commit local, aún sin push |
| Los dos commits de CI no se hicieron: los archivos nuevos estaban solo como `git add -N` (intent-to-add) | La salida de `git commit` («no changes added to commit») | `git add` y commit de nuevo |
| Marqué `[x]` B.52, FE.52 y B.55, cuyo texto incluía el ruleset y la URL de producción, que dependían de paneles del usuario | Al releer `tareas.md` antes de mostrar el diff | Se dejaron `[ ]` con su nota, y se cerraron cuando el usuario los hizo |
| Di por «verificado» el límite por IP con una sola red, que no distingue entre la IP real y la del proxy | Razonando sobre el resultado del usuario | Se marcó parcial (`[~]`) hasta la prueba desde otra red; después, `[x]` |

### Prompts clave
- *"Lee PLAN_CLAUDE_CODE.md (Fase 5)… una tarea por commit, mostrando el diff de cada una y esperando mi aprobación. Sin push."* → plan en modo plan y tareas 5.1–5.5.
- *"Sobre generateStaticParams: sí, como tarea 5.4b… que el build no dependa de que Render esté despierto"* → de 843 a 151 MB y el hallazgo del build con `API_URL`.
- *"Opción 1. Arregla /creditos y TODAS las páginas que fallen en next build con API_URL… contra el backend local encendido y también contra un puerto muerto"* → `<Suspense>` en `/creditos` y `/regiones`.
- *"Nunca me pidas los secretos ni los escribas en archivos"* → las variables van solo por nombre en `docs/despliegue.md`; los valores, en los paneles de Render y Vercel.
- **Uso (dato del usuario):** unos 40 prompts (conteo aproximado) y 24 % del límite semanal de Claude (que se reinició el mismo día).

### Arranque en frío (hallazgo y corrección, misma jornada)
- Los logs dieron 116–134 s de arranque en Render, más que el «hasta un minuto» escrito en los docs y que los topes del front (70 s y 110 s): una primera visita con el servidor dormido podía dar 504.
- **Backend:** con el contenedor a 0,1 CPU y 512 MB, `Started` pasó de 180–188 s a **65 s** (−64 %): `-XX:TieredStopAtLevel=1` (−43 %) y un archivo CDS generado en el build (−26 % más); `UseSerialGC`, `lazy-initialization` y `ddl-auto=none` no mejoraron nada medible. Detalle en `docs/despliegue.md`. **Medido en Render producción tras el despliegue: `Started … in 47.893 seconds`, frente a 122 s antes (−61 %)**; el banco local (−64 %) predijo bien la mejora.
- **Frontend:** topes de 180 s (lecturas) y 270 s (identificar, con `maxDuration` 300) y aviso «La primera vez puede tardar hasta 2–3 minutos…».
- Error mío: la primera prueba de `-XX:-BytecodeVerificationRemote` falló por falta de `-XX:+UnlockDiagnosticVMOptions`; se descartó sin repetirla (opción obsoleta y que quita una comprobación de seguridad). Mi primer `Dockerfile` con CDS dejó la imagen en 562 MB por capas duplicadas (`chown -R` y el JAR); se reorganizó y quedó en 454 MB.
