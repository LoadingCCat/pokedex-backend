package com.pokedex.exception;

/**
 * F20: la IA no reconoció un Pokémon, lo reconoció con confianza menor a 0,6 o su slug no
 * existe en la base (422). Los tres casos dan el mismo mensaje a propósito: el cliente no
 * necesita saber cuál fue.
 */
public class PokemonNoReconocidoException extends RuntimeException {

    public PokemonNoReconocidoException() {
        super("No reconocimos un Pokémon en la foto. Prueba con otra más clara");
    }
}
