package com.pokedex.exception;

/** Se lanza cuando la petición trae datos inválidos (responde 400). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String mensaje) {
        super(mensaje);
    }
}
