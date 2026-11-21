package com.pokedex.util;

import java.util.Locale;
import java.util.Map;

/** Nombre en español de los 18 tipos (la BD guarda el nombre en inglés de la PokeAPI). */
public final class TiposEspanol {

    static final Map<String, String> NOMBRES = Map.ofEntries(
            Map.entry("normal", "Normal"),
            Map.entry("fire", "Fuego"),
            Map.entry("water", "Agua"),
            Map.entry("electric", "Eléctrico"),
            Map.entry("grass", "Planta"),
            Map.entry("ice", "Hielo"),
            Map.entry("fighting", "Lucha"),
            Map.entry("poison", "Veneno"),
            Map.entry("ground", "Tierra"),
            Map.entry("flying", "Volador"),
            Map.entry("psychic", "Psíquico"),
            Map.entry("bug", "Bicho"),
            Map.entry("rock", "Roca"),
            Map.entry("ghost", "Fantasma"),
            Map.entry("dragon", "Dragón"),
            Map.entry("steel", "Acero"),
            Map.entry("dark", "Siniestro"),
            Map.entry("fairy", "Hada"));

    private TiposEspanol() {
    }

    /** Traduce el tipo sin importar mayúsculas; si no lo conoce, devuelve el nombre tal cual. */
    public static String de(String nombreIngles) {
        if (nombreIngles == null) {
            return null;
        }
        return NOMBRES.getOrDefault(nombreIngles.toLowerCase(Locale.ROOT), nombreIngles);
    }
}
