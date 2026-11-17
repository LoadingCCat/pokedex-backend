package com.pokedex.exception;

/** 401: credenciales incorrectas en el login (F15). */
public class NoAutorizadoException extends RuntimeException {

    public NoAutorizadoException(String mensaje) {
        super(mensaje);
    }
}
