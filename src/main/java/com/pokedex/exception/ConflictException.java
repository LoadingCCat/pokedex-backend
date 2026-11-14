package com.pokedex.exception;

/** Se lanza cuando la operación choca con el estado actual del servidor (responde 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String mensaje) {
        super(mensaje);
    }
}
