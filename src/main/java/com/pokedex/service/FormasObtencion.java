package com.pokedex.service;

import java.text.Normalizer;
import java.util.List;

/** Los 9 valores de tipoObtencion y su lectura con o sin tilde (fosil = Fósil). */
public final class FormasObtencion {

    public static final List<String> VALORES = List.of(
            "Inicial", "Singular", "Fósil", "Legendario", "Salvaje", "Regalo", "Huevo", "Evolución", "Sin datos");

    private FormasObtencion() {
    }

    /** "fosil", "FÓSIL" o "Fósil" → "Fósil"; un valor que no existe → null. */
    public static String oficial(String valor) {
        if (valor == null) {
            return null;
        }
        String buscado = sinTildes(valor.trim());
        return VALORES.stream().filter(t -> sinTildes(t).equalsIgnoreCase(buscado)).findFirst().orElse(null);
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
