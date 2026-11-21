package com.pokedex.exception;

/**
 * El servicio de visión no respondió a tiempo, falló, devolvió algo inesperado o no está
 * configurado (F20). Más adelante se traduce a 502. El mensaje es genérico a propósito:
 * nunca lleva la clave, la URL del proveedor ni la respuesta cruda de la IA.
 */
public class VisionNoDisponibleException extends RuntimeException {

    public VisionNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
