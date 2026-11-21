---
name: backend-seguro
description: Reglas de seguridad para cualquier cambio del backend de Pokedex+ (Spring Boot) que toque datos, endpoints, autenticación, consultas, errores, secretos o subida de archivos. Úsala siempre antes de dar por terminado un cambio de ese tipo.
---

# Backend seguro (Spring Boot + JPA + JWT)

Se aplica **siempre** que el cambio lea o escriba datos: controllers, servicios, repositorios, entidades, `SeguridadConfig`, `GlobalExceptionHandler`, configuración. Complementa a `nuevo-endpoint`; no lo reemplaza. El modelo y quién escribe cada tabla están en `docs/datos/modelo.md`.

Toda validación que importe se hace en el servidor. Lo que valide el frontend es comodidad, no seguridad.

## 1. Validar en el servidor
- Cada dato del cuerpo, de la ruta o de la query se valida en el controller o en un servicio, **antes** de tocar la BD o un servicio externo.
- Errores del cliente: `BadRequestException` (400), `CamposInvalidosException` (400 con `campos`), `ResourceNotFoundException` (404), `ConflictException` (409). No dejes escapar otra excepción a propósito.
- Los límites salen del contrato o de la spec (rangos, largos, valores permitidos). Si no están, defínelos ahí primero.
- Un cuerpo recibido como entidad (`@RequestBody Movimiento`) se limpia: pon en `null` el id y todo campo que solo asigna el servidor (`idRuta`, `idPokeapi`, `esCompetitivo`, `editadoAMano`). Mejor aún: recibir un `Map` o un `record` y copiar solo los campos permitidos, como hace `DatosPokemon`.
- Si una regla debe cumplirse siempre, que también la proteja la BD (`UNIQUE`, `NOT NULL`, FK), no solo el código. Anótalo en `docs/datos/modelo.md`.

## 2. El rol sale del JWT, nunca del cuerpo
- El rol vive en el claim `rol` del token, firmado con `JWT_SECRETO` y convertido a `ROLE_ADMIN` en `SeguridadConfig`. Ningún `@RequestBody`, parámetro ni cabecera propia puede traer ni cambiar un rol, un id de usuario ni `esAdmin`.
- Las reglas de acceso van en `SeguridadConfig`. Hoy: `GET` público (salvo la importación), el resto `hasRole(ADMIN)` por `anyRequest()`. Un endpoint de escritura nuevo queda protegido sin hacer nada.
- **Una excepción pública es una decisión, no un descuido.** Hoy son `POST /api/auth/login` y, con F20, `POST /api/pokemons/identificar`. Una nueva se declara en `SeguridadConfig` con su `requestMatchers(...).permitAll()`, tiene spec y lleva dos pruebas: responde sin token y los demás endpoints de escritura siguen en 401.
- Un endpoint público que gasta recursos (cuota de la IA, CPU) lleva límite de uso (F20: 10 por minuto por IP).
- Si llega `Authorization`, tiene que ser válido: un token malo es 401 aunque el endpoint sea público.

## 3. Consultas con parámetros
- Spring Data: métodos derivados, `@Query` con parámetros nombrados (`:nombre` + `@Param`) o `Specification` (`PokemonFiltros`).
- **Nunca** concatenes texto del usuario en JPQL, en SQL nativo ni en un `sort`. Las `@Query` nativas, como `desvincularDePokemons`, también usan `@Param`.
- El orden (`?sort=`) lo valida Spring Data; un campo inexistente es 400, no 500.
- `LIKE` con texto del usuario: pásalo como parámetro, nunca dentro del literal.

## 4. Errores discretos
- Todo error sale de `GlobalExceptionHandler` (y los 401/403 de los filtros, de `ErroresSeguridad`) con el mismo JSON `{ timestamp, status, error, mensaje, path }`.
- El 500 lleva un mensaje genérico. El detalle (SQL, trazas, `ex.getMessage()`) va solo a `log.error`, nunca a la respuesta.
- Un servicio externo que falla (F20): 502 con el mensaje del contrato, sin la URL del proveedor, la clave ni la respuesta cruda.
- El login no dice si falló el usuario o la contraseña, y tarda lo mismo en ambos casos (`AuthController`).
- Una excepción nueva de Spring (p. ej. `MaxUploadSizeExceededException`, `MultipartException`) se **maneja en `GlobalExceptionHandler`**. Si no, cae en el 500 genérico y el contrato promete un 400.
- No loguees contraseñas, tokens, claves ni el contenido de las fotos.

## 5. Secretos solo en variables de entorno
- Nombres actuales: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRETO` (≥ 32 caracteres), `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `CORS_ORIGENES`. Con F20: `GEMINI_API_KEY`, `GEMINI_MODELO`.
- En el código y en los docs va solo el **nombre** de la variable, nunca su valor. Sin valor por defecto para un secreto: si falta, el servidor no arranca (como `JWT_SECRETO`) o la función responde 502 (como la IA sin clave).
- Nunca leas, muestres ni subas `application-local.properties` ni `.env*`; excluyelos de cualquier `grep`.
- `CORS_ORIGENES` lista los orígenes permitidos; nunca `*`.

## 6. Subida de archivos (`multipart`)
- Límites del servidor: `spring.servlet.multipart.max-file-size=5MB` y `max-request-size=6MB` (se agregan en la tarea 2.1; sin ellos, Spring usa 1 MB y 10 MB por defecto).
- Además, el controller valida: el campo existe, no está vacío, pesa ≤ 5 MB y el tipo real (JPEG, PNG o WEBP) se comprueba por los **primeros bytes**, no por `Content-Type` ni por la extensión.
- Todo eso ocurre **antes** de llamar a un servicio externo.
- El archivo no se guarda (ni disco ni BD) salvo que una spec lo pida, y no se escribe en el log.
- Nunca uses el nombre del archivo del cliente para armar una ruta en disco.

## 7. Al terminar: informa
Cierra siempre con estas dos cosas, para el cambio concreto que hiciste:

**a) Criterios que cubre el cambio.** Una lista de los criterios de aceptación de la spec (y de esta skill) que quedan cumplidos, cada uno con la prueba automática que lo cubre. Si alguno no se cubre, dilo.

**b) Cómo probar al menos un caso de error sin usar el formulario.** Con `curl.exe`, contra el servidor local. Elige el que corresponda al cambio; ejemplos:

```powershell
# Sin token en una escritura → 401
curl.exe -i -X POST http://localhost:8080/api/rutas -H "Content-Type: application/json" --data-binary "@vacio.json"

# Con token ADMIN y cuerpo inválido → 400 con "campos"
curl.exe -i -X POST http://localhost:8080/api/movimientos -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" --data-binary "@malo.json"

# Un rol enviado en el cuerpo se ignora: sin token sigue siendo 401
curl.exe -i -X POST http://localhost:8080/api/rutas -H "Content-Type: application/json" --data-binary "@con-rol.json"

# F20: un .txt renombrado a .jpg → 400 "La foto debe ser JPG, PNG o WEBP"
curl.exe -i -X POST http://localhost:8080/api/pokemons/identificar -F "foto=@falsa.jpg"
```

Los `.json` de prueba los creas tú en una carpeta temporal (no en el repo). `$TOKEN` sale de `POST /api/auth/login` y no se escribe en ningún archivo ni en ningún doc. Indica en el informe el código esperado y el código que obtuviste.
