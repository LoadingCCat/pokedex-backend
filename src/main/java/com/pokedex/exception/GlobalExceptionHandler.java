package com.pokedex.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    // 405: método HTTP no permitido (ej. DELETE sobre un endpoint que solo acepta GET)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> manejarMetodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                                                        HttpServletRequest request) {
        return construir(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
                "El método " + ex.getMethod() + " no está soportado en esta ruta", request);
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
            return construir(HttpStatus.BAD_REQUEST, "Petición inválida",
                    "El parámetro 'sort' no es válido: " + ordenInvalido.getMessage(), request);
        }

        // El detalle real (que puede incluir SQL o datos internos) solo va al log del servidor
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor",
                "Ocurrió un error inesperado. Revisa el log del servidor para más detalles.", request);
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
