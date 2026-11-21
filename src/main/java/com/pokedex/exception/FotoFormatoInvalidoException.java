package com.pokedex.exception;

/** F20: los primeros bytes del archivo no son de un JPEG, PNG ni WEBP (400). */
public class FotoFormatoInvalidoException extends BadRequestException {

    public FotoFormatoInvalidoException() {
        super("La foto debe ser JPG, PNG o WEBP");
    }
}
