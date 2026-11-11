package com.pokedex.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
@SuppressWarnings({"rawtypes", "unchecked"})
public class GlobalExceptionHandler {

    // Manejo de recursos no encontrados (Ej. una URL mal escrita en el frontend)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity manejarRecursoNoEncontrado(NoResourceFoundException ex) {
        Map response = new LinkedHashMap();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", "Recurso no encontrado");
        response.put("path", ex.getResourcePath());
        
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // Manejo de cualquier otro error general (Evita que la API se caiga)
    @ExceptionHandler(Exception.class)
    public ResponseEntity manejarErroresGlobales(Exception ex) {
        Map response = new LinkedHashMap();
        response.put("timestamp", LocalDateTime.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Error interno del servidor");
        response.put("mensaje", ex.getMessage());

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}