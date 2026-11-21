package com.pokedex.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TiposEspanolTest {

    @Test
    void cubreLosDieciochoTiposConSuNombreEnEspanol() {
        Map<String, String> esperado = Map.ofEntries(
                Map.entry("normal", "Normal"), Map.entry("fire", "Fuego"), Map.entry("water", "Agua"),
                Map.entry("electric", "Eléctrico"), Map.entry("grass", "Planta"), Map.entry("ice", "Hielo"),
                Map.entry("fighting", "Lucha"), Map.entry("poison", "Veneno"), Map.entry("ground", "Tierra"),
                Map.entry("flying", "Volador"), Map.entry("psychic", "Psíquico"), Map.entry("bug", "Bicho"),
                Map.entry("rock", "Roca"), Map.entry("ghost", "Fantasma"), Map.entry("dragon", "Dragón"),
                Map.entry("steel", "Acero"), Map.entry("dark", "Siniestro"), Map.entry("fairy", "Hada"));

        assertThat(esperado).hasSize(18);
        esperado.forEach((ingles, espanol) -> assertThat(TiposEspanol.de(ingles)).isEqualTo(espanol));
        assertThat(TiposEspanol.NOMBRES).hasSize(18);
    }

    @Test
    void noImportanLasMayusculas() {
        assertThat(TiposEspanol.de("FIRE")).isEqualTo("Fuego");
        assertThat(TiposEspanol.de("Water")).isEqualTo("Agua");
    }

    @Test
    void unTipoDesconocidoSeDevuelveTalCualYNuloDaNulo() {
        assertThat(TiposEspanol.de("stellar")).isEqualTo("stellar");
        assertThat(TiposEspanol.de(null)).isNull();
    }
}
