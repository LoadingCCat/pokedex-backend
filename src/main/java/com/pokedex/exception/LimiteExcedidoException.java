package com.pokedex.exception;

/**
 * F20: una IP superó las 10 identificaciones por minuto. Se traduce a 429 en la tarea 2.5
 * (todavía no hay controlador ni manejador).
 */
public class LimiteExcedidoException extends RuntimeException {

    public LimiteExcedidoException() {
        super("Demasiadas fotos seguidas. Espera un minuto");
    }
}
