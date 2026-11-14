# Pokedex+ — Backend

API REST de la Pokedex+ hecha con Java 21, Spring Boot 4.1.2, Maven y PostgreSQL. Los datos se importan desde la [PokeAPI](https://pokeapi.co/).

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

## Documentación del proyecto (SDD)

| Documento | Qué contiene |
|---|---|
| [docs/producto.md](docs/producto.md) | Qué es, para quién y qué no entra |
| [docs/arquitectura.md](docs/arquitectura.md) | Stack, capas y modelo de datos |
| [docs/contratos/api.md](docs/contratos/api.md) | Contrato de la API para el frontend |
| [docs/specs/](docs/specs/) | Una spec por funcionalidad, con criterios de aceptación |
| [docs/decisiones.md](docs/decisiones.md) | Decisiones tomadas y por qué |
| [docs/tareas.md](docs/tareas.md) | Qué está hecho y qué sigue |
| [BITACORA_IA.md](BITACORA_IA.md) | Prompts clave y errores del agente |
| [CLAUDE.md](CLAUDE.md) | Instrucciones para el agente |
