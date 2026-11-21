package com.pokedex.exception;

/** F20: falta el campo {@code foto} o llegó vacío (400). Hereda de {@link BadRequestException}. */
public class FotoFaltanteException extends BadRequestException {

    public FotoFaltanteException() {
        super("Envía una foto en el campo foto");
    }
}
