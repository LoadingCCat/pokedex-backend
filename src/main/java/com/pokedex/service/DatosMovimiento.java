package com.pokedex.service;

/**
 * Cuerpo de POST y PUT /api/movimientos (S-02). Solo trae los campos que el cliente puede fijar:
 * el id y `esCompetitivo` los asigna el servidor, así que, si llegan en el JSON, se ignoran.
 * Los largos máximos son los de las columnas de `movimiento` (ver docs/datos/modelo.md).
 */
public record DatosMovimiento(String nombre, String nombreEs, String tipo, String categoria, Integer potencia) {

    public static final int MAX_NOMBRE = 100;
    public static final int MAX_NOMBRE_ES = 100;
}
