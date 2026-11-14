package com.pokedex.exception;

/** Se lanza cuando un recurso pedido por ID no existe (responde 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String mensaje) {
        super(mensaje);
    }

    public ResourceNotFoundException(String recurso, Object id) {
        super(recurso + " con id " + id + " no existe");
    }
}
