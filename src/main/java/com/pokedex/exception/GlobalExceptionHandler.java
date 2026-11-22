package com.pokedex.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convierte cualquier error en un JSON con el mismo formato:
 * { "timestamp", "status", "error", "mensaje", "path" }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MENSAJE_SIN_FOTO = "Envía una foto en el campo foto";

    // 404: la URL no existe (ej. una ruta mal escrita en el frontend)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> manejarRutaNoEncontrada(NoResourceFoundException ex,
                                                                       HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, "Recurso no encontrado",
                "La ruta solicitada no existe", request);
    }

    // 404: el recurso pedido por ID no existe (ej. /api/pokemons/99999)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> manejarNoEncontrado(ResourceNotFoundException ex,
                                                                   HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
    }

    // 400: datos inválidos que detectamos nosotros
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> manejarPeticionInvalida(BadRequestException ex,
                                                                       HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "Petición inválida", ex.getMessage(), request);
    }

    // 400 con el detalle por campo (formularios del panel, F15): suma "campos" al JSON de siempre
    @ExceptionHandler(CamposInvalidosException.class)
    public ResponseEntity<Map<String, Object>> manejarCamposInvalidos(CamposInvalidosException ex,
                                                                      HttpServletRequest request) {
        ResponseEntity<Map<String, Object>> respuesta =
                construir(HttpStatus.BAD_REQUEST, "Petición inválida", ex.getMessage(), request);
        respuesta.getBody().put("campos", ex.getCampos());
        return respuesta;
    }

    // 400: tipo de parámetro incorrecto (ej. /api/pokemons/abc o ?total=xyz)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> manejarTipoIncorrecto(MethodArgumentTypeMismatchException ex,
                                                                     HttpServletRequest request) {
        String mensaje = "El parámetro '" + ex.getName() + "' tiene un valor inválido: " + ex.getValue();
        return construir(HttpStatus.BAD_REQUEST, "Petición inválida", mensaje, request);
    }

    // 400: falta un parámetro obligatorio (ej. /api/pokemons/buscar sin ?nombre=)
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> manejarParametroFaltante(MissingServletRequestParameterException ex,
                                                                        HttpServletRequest request) {
        String mensaje = "Falta el parámetro obligatorio '" + ex.getParameterName() + "'";
        return construir(HttpStatus.BAD_REQUEST, "Petición inválida", mensaje, request);
    }

    // 400: el cuerpo JSON de un POST/PUT está mal formado
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarJsonInvalido(HttpMessageNotReadableException ex,
                                                                   HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "Petición inválida",
                "El cuerpo de la petición no es un JSON válido", request);
    }

    // 400 (F20): falta la parte "foto" del multipart o el multipart llegó mal formado
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<Map<String, Object>> manejarMultipartInvalido(Exception ex, HttpServletRequest request) {
        // MaxUploadSizeExceededException es un MultipartException: se atiende en su propio método
        return construir(HttpStatus.BAD_REQUEST, "Petición inválida", MENSAJE_SIN_FOTO, request);
    }

    // 400 (F20): el servidor cortó la subida por pasar max-file-size / max-request-size (no es un 500)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> manejarSubidaMuyGrande(MaxUploadSizeExceededException ex,
                                                                      HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "Petición inválida", "La foto pesa más de 5 MB", request);
    }

    // 422 (F20): no hay Pokémon en la foto, confianza baja o slug que no está en la base
    @ExceptionHandler(PokemonNoReconocidoException.class)
    public ResponseEntity<Map<String, Object>> manejarNoReconocido(PokemonNoReconocidoException ex,
                                                                   HttpServletRequest request) {
        return construir(HttpStatus.UNPROCESSABLE_ENTITY, "No se pudo procesar", ex.getMessage(), request);
    }

    // 429: más de 10 fotos (F20) o 5 logins fallidos (S-01) por minuto desde la misma IP
    @ExceptionHandler(LimiteExcedidoException.class)
    public ResponseEntity<Map<String, Object>> manejarLimiteExcedido(LimiteExcedidoException ex,
                                                                     HttpServletRequest request) {
        // N-03: las dos ventanas (login e identificar) son de un minuto
        ResponseEntity<Map<String, Object>> respuesta =
                construir(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas peticiones", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "60").body(respuesta.getBody());
    }

    // 502 (F20): el servicio de visión falló o no está configurado. El mensaje es fijo: nunca la
    // clave, la URL del proveedor ni la respuesta cruda; lo que haya va solo al log (sin traza)
    @ExceptionHandler(VisionNoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> manejarVisionNoDisponible(VisionNoDisponibleException ex,
                                                                         HttpServletRequest request) {
        log.warn("Servicio de visión no disponible en {}: {}", request.getRequestURI(), ex.getMessage());
        return construir(HttpStatus.BAD_GATEWAY, "Servicio externo no disponible",
                "El reconocimiento no está disponible en este momento", request);
    }

    // 415 (S-04): el Content-Type del cuerpo no es uno que la ruta acepte (ej. text/plain en un login JSON)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> manejarTipoDeContenidoNoSoportado(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return construir(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado",
                "El tipo de contenido de la petición no es compatible con esta ruta. Usa application/json", request);
    }

    // 406 (S-04): el cliente pide en Accept un formato que la API no produce
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> manejarTipoDeRespuestaNoAceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return construir(HttpStatus.NOT_ACCEPTABLE, "Formato no aceptable",
                "La API solo responde en application/json", request);
    }

    // 405: método HTTP no permitido (ej. DELETE sobre un endpoint que solo acepta GET)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> manejarMetodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                                                        HttpServletRequest request) {
        return construir(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
                "El método " + ex.getMethod() + " no está soportado en esta ruta", request);
    }

    // 401: usuario o contraseña incorrectos en POST /api/auth/login (F15). Los 401 y 403 por
    // falta de token o de rol los responde ErroresSeguridad, con este mismo formato
    @ExceptionHandler(NoAutorizadoException.class)
    public ResponseEntity<Map<String, Object>> manejarNoAutorizado(NoAutorizadoException ex,
                                                                   HttpServletRequest request) {
        return construir(HttpStatus.UNAUTHORIZED, "No autorizado", ex.getMessage(), request);
    }

    // 409: la operación choca con el estado actual (ej. ya hay una importación en curso)
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> manejarConflicto(ConflictException ex, HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, "Conflicto", ex.getMessage(), request);
    }

    // 500: cualquier otro error (evita que la API devuelva trazas de Java)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> manejarErroresGlobales(Exception ex, HttpServletRequest request) {
        // 400: ?sort= con un campo que no existe (ej. ?sort=foo). Spring Data lanza
        // PropertyReferenceException, a veces envuelta en otra excepción; se busca por nombre
        // para no depender del paquete de esa clase, que cambia entre versiones de Spring Data.
        Throwable ordenInvalido = buscarCausa(ex, "PropertyReferenceException");
        if (ordenInvalido != null) {
            // S-03: no se usa su mensaje (trae el nombre de la clase y del campo internos)
            return construir(HttpStatus.BAD_REQUEST, "Petición inválida", mensajeSortInvalido(request), request);
        }
        // En las consultas con @Query el orden se agrega al JPQL y quien falla es Hibernate
        // (UnknownPathException). Solo se trata como 400 si la petición traía ?sort=; sin él
        // sería un error real de la consulta. No se usa su mensaje porque incluye el JPQL.
        if (request.getParameter("sort") != null && buscarCausa(ex, "UnknownPathException") != null) {
            return construir(HttpStatus.BAD_REQUEST, "Petición inválida", mensajeSortInvalido(request), request);
        }

        // El detalle real (que puede incluir SQL o datos internos) solo va al log del servidor
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor",
                "Ocurrió un error inesperado. Revisa el log del servidor para más detalles.", request);
    }

    private static final int MAX_CAMPO_SORT_EN_MENSAJE = 40;

    /** Mensaje fijo: solo repite el campo que el propio cliente envió en ?sort=, nunca nombres internos. */
    private static String mensajeSortInvalido(HttpServletRequest request) {
        String base = "El parámetro 'sort' no es válido";
        String sort = request.getParameter("sort");
        String campo = sort == null ? "" : sort.split(",")[0].trim();
        // N-05: se repite solo el principio de lo que envió el cliente
        if (campo.length() > MAX_CAMPO_SORT_EN_MENSAJE) {
            campo = campo.substring(0, MAX_CAMPO_SORT_EN_MENSAJE) + "…";
        }
        return campo.isEmpty() ? base : base + ": el campo '" + campo + "' no existe";
    }

    /** Recorre la cadena de causas buscando una excepción por su nombre de clase. */
    private static Throwable buscarCausa(Throwable ex, String nombreClase) {
        for (Throwable actual = ex; actual != null; actual = actual.getCause()) {
            if (actual.getClass().getSimpleName().equals(nombreClase)) {
                return actual;
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> construir(HttpStatus status, String error, String mensaje,
                                                          HttpServletRequest request) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", LocalDateTime.now());
        cuerpo.put("status", status.value());
        cuerpo.put("error", error);
        cuerpo.put("mensaje", mensaje);
        cuerpo.put("path", request.getRequestURI());
        return new ResponseEntity<>(cuerpo, status);
    }
}
