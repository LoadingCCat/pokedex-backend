package com.pokedex.service;

/**
 * Lo único que la IA aporta (F20): si hay un Pokémon en la foto, su slug en inglés tal como
 * lo usa la PokeAPI (por ejemplo {@code mr-mime}, en minúsculas) y qué tan segura está (0 a 1).
 * Si {@code identificado} es false, {@code slug} es null.
 */
public record ResultadoVision(boolean identificado, String slug, double confianza) {
}
