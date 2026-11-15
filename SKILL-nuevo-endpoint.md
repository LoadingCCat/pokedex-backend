---
name: nuevo-endpoint
description: Agregar o modificar un endpoint o un campo de la API REST de Pokedex+ siguiendo las convenciones del proyecto. Úsala cada vez que la tarea toque controllers, entidades, repositorios o el contrato de la API.
---

# Cómo agregamos un endpoint en este proyecto

1. **Primero el doc.** Lee la spec de la funcionalidad en `docs/specs/`. Si el endpoint o el campo no está en la spec ni en `docs/contratos/api.md`, propón primero el cambio en esos docs y espera aprobación.
2. **Entidad.** Si hay campos nuevos, deben aceptar `null` (`ddl-auto=update` no rellena registros existentes). Nombres en español, con `@Column(name = "snake_case")` cuando tengan varias palabras. Una relación con muchos elementos lleva `@JsonIgnore` y se expone en su propio endpoint.
3. **Repositorio.** Métodos derivados con `IgnoreCase`, o `@Query` explícita con `LOWER()`. Con `JOIN` + paginación: `DISTINCT` y un `countQuery` propio. No uses `JOIN FETCH` ni `@EntityGraph` sobre dos colecciones `List`.
4. **Controller.**
   - Inyección por constructor.
   - Valida la entrada y lanza `BadRequestException` (400), `ResourceNotFoundException` (404) o `ConflictException` (409). Nunca dejes escapar otra excepción a propósito.
   - Las listas de Pokémon devuelven `Page<Pokemon>` y reciben un `Pageable`.
   - Si recibe una entidad por `@RequestBody`, pon en `null` el id y los campos que solo asigna el servidor.
5. **Servicio.** Lógica de importación en `PokemonService`. Lee el JSON de la PokeAPI con `getJson`, `mapa` y `lista`. Si falla una parte opcional, registra un `log.warn` y sigue.
6. **Contrato.** Actualiza `docs/contratos/api.md` (tabla de endpoints, ejemplo de JSON y errores) en el mismo commit.
7. **Verificar.**
   - `.\mvnw.cmd clean compile`
   - Arranca el servidor y prueba con `curl.exe` cada criterio de aceptación de la spec, incluidos los casos de error (400 / 404).
   - Comprueba que los endpoints que ya existían responden igual que antes.
8. **Cerrar.** Marca los criterios cumplidos en la spec, la tarea en `docs/tareas.md`, y anota en `docs/decisiones.md` cualquier decisión técnica. Commit con un mensaje corto en español.
