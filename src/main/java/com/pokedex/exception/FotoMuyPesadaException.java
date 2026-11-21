package com.pokedex.exception;

/** F20: la foto pesa más de 5 MB (400). */
public class FotoMuyPesadaException extends BadRequestException {

    public FotoMuyPesadaException() {
        super("La foto pesa más de 5 MB");
    }
}
