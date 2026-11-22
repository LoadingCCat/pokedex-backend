package com.pokedex.service;

/**
 * Cuerpo de POST y PUT /api/rutas (S-02). Solo trae lo que el cliente puede fijar: `idRuta` e
 * `idPokeapi` los asigna el servidor (este último marca las rutas importadas), así que, si llegan
 * en el JSON, se ignoran. Los largos son los de las columnas de `ruta`; `descripcion` es TEXT en
 * la BD y aquí tiene un tope de 2000, como la de Pokémon.
 */
public record DatosRuta(String nombre, String region, String descripcion) {

    public static final int MAX_NOMBRE = 100;
    public static final int MAX_REGION = 50;
    public static final int MAX_DESCRIPCION = 2000;
}
