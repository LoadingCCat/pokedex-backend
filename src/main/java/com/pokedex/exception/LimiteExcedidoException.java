package com.pokedex.exception;

/**
 * Una IP superó un límite de uso en memoria: 10 identificaciones por minuto (F20) o 5 intentos
 * de login fallidos por minuto (S-01). Se traduce a 429 en GlobalExceptionHandler.
 */
public class LimiteExcedidoException extends RuntimeException {

    public LimiteExcedidoException() {
        super("Demasiadas fotos seguidas. Espera un minuto");
    }

    public LimiteExcedidoException(String mensaje) {
        super(mensaje);
    }
}
