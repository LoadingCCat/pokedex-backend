# Pokedex+ — Backend

API REST de la Pokedex+ hecha con Java 21, Spring Boot 4.1.2, Maven y PostgreSQL. Los datos se importan desde la [PokeAPI](https://pokeapi.co/).

**Frontend publicado:** <https://pokedex-frontend-seven.vercel.app/> (código en [LoadingCCat/pokedex-frontend](https://github.com/LoadingCCat/pokedex-frontend)). La versión publicada usa esta API desplegada en Render.

**API en producción:** <https://pokedex-backend-5bcq.onrender.com/api> (comprobación: [`/api/health`](https://pokedex-backend-5bcq.onrender.com/api/health)) · **API de desarrollo:** <https://pokedex-backend-dev.onrender.com/api>. El plan gratuito de Render se duerme tras 15 minutos sin peticiones: la primera llamada puede tardar hasta un minuto. Para conectar el frontend a un backend local, ver su README (`API_URL`).

## Cómo correrlo

1. Crea la base de datos `pokedex_db` en PostgreSQL.
2. Copia `src/main/resources/application-local.properties.example` como `application-local.properties` en la misma carpeta y pon tu usuario y contraseña. Ese archivo no se sube a git.
3. Ejecuta:
   ```
   .\mvnw.cmd spring-boot:run
   ```
4. Llena la base de datos (tarda unos minutos porque consulta la PokeAPI):
   ```
   http://localhost:8080/api/pokemons/importar-rango?total=151&movimientos=100
   ```
   Para todos los Pokémon usa `total=1025`. Se puede ejecutar varias veces: actualiza lo que ya existe y no duplica nada.
   La primera vez tarda más (unos minutos para 151) porque también descarga los lugares donde aparece cada Pokémon y los movimientos que aprende.

## API

Base URL: `http://localhost:8080/api`

Endpoints, formatos de respuesta, paginación y errores: **[docs/contratos/api.md](docs/contratos/api.md)**.

## Docker y CI

```
docker build -t pokedex-backend .
```

El `Dockerfile` (multi-etapa, JDK 21 → JRE 21, usuario sin privilegios) escucha en `PORT` (8080 por defecto) y lee la base de `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` (o `DB_*`). El CI de GitHub Actions (`.github/workflows/ci.yml`) corre `./mvnw verify` y construye la imagen en cada push a `main`.

## Despliegue

Render (backend) + Neon (PostgreSQL) + Vercel (frontend), todo en US East. Paso a paso, variables (solo nombres), rollback y verificación: **[docs/despliegue.md](docs/despliegue.md)**.

## Documentación del proyecto (SDD)

| Documento | Qué contiene |
|---|---|
| [docs/producto.md](docs/producto.md) | Qué es, para quién y qué no entra |
| [docs/arquitectura.md](docs/arquitectura.md) | Stack, capas y modelo de datos |
| [docs/contratos/api.md](docs/contratos/api.md) | Contrato de la API para el frontend |
| [docs/specs/](docs/specs/) | Una spec por funcionalidad, con criterios de aceptación |
| [docs/despliegue.md](docs/despliegue.md) | Render, Neon y Vercel: pasos, variables, rollback y arquitectura |
| [docs/decisiones.md](docs/decisiones.md) | Decisiones tomadas y por qué |
| [docs/tareas.md](docs/tareas.md) | Qué está hecho y qué sigue |
| [BITACORA_IA.md](BITACORA_IA.md) | Prompts clave y errores del agente |
| [CLAUDE.md](CLAUDE.md) | Instrucciones para el agente |
