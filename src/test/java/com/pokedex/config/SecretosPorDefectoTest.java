package com.pokedex.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S-08 y S-09 (interventoría): ni la contraseña de la BD ni el secreto del token tienen un valor
 * por defecto o de ejemplo que arranque el servidor sin que nadie lo haya configurado.
 */
class SecretosPorDefectoTest {

    private final SeguridadConfig config = new SeguridadConfig();

    private static Map<String, Object> propiedades(String recurso) throws Exception {
        Properties p = new Properties();
        try (InputStream in = new ClassPathResource(recurso).getInputStream()) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Map<String, Object> mapa = new HashMap<>();
        p.forEach((k, v) -> mapa.put((String) k, v));
        return mapa;
    }

    // ---------- S-08 · contraseña de la BD sin valor por defecto ----------

    @Test
    void sinVariablesDeEntornoLaContrasenaDeLaBdNoSeResuelve() throws Exception {
        // MockEnvironment no ve las variables reales de la máquina: solo application.properties
        MockEnvironment entorno = new MockEnvironment();
        entorno.getPropertySources().addLast(new MapPropertySource("app", propiedades("application.properties")));

        assertThatThrownBy(() -> entorno.getProperty("spring.datasource.password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Could not resolve placeholder");
    }

    @Test
    void conLaVariableDefinidaLaContrasenaSeLee() throws Exception {
        for (String variable : new String[]{"SPRING_DATASOURCE_PASSWORD", "DB_PASSWORD"}) {
            MockEnvironment entorno = new MockEnvironment().withProperty(variable, "valor-de-prueba");
            entorno.getPropertySources().addLast(new MapPropertySource("app", propiedades("application.properties")));

            assertThat(entorno.getProperty("spring.datasource.password")).isEqualTo("valor-de-prueba");
        }
    }

    // ---------- S-09 · secreto JWT de ejemplo ----------

    @Test
    void elSecretoDeLaPlantillaLocalSeRechazaAlArrancar() throws Exception {
        String secreto = (String) propiedades("application-local.properties.example").get("JWT_SECRETO");

        assertThat(secreto).isNotBlank();
        assertThat(secreto.getBytes(StandardCharsets.UTF_8).length).isGreaterThanOrEqualTo(32); // no cae por corto
        assertThatThrownBy(() -> config.claveJwt(secreto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRETO")
                .hasMessageNotContaining(secreto);
    }

    @Test
    void tambienSeRechazaElSecretoDeRellenoDelEntrenamiento() {
        String secreto = "entrenamiento-sin-valor-real-" + "x".repeat(20);

        assertThatThrownBy(() -> config.claveJwt(secreto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(secreto);
    }

    @Test
    void unSecretoPropioDeLargoSuficienteSeAcepta() {
        SecretKey clave = config.claveJwt("N7vQ2xLp9TzR4mKd8WcY1bHs6JfA3eUg5oXi0CtZ");

        assertThat(clave.getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void sinSecretoOConUnoCortoSigueSinArrancar() {
        assertThatThrownBy(() -> config.claveJwt("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.claveJwt("corto")).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> config.claveJwt("N7vQ2xLp9TzR4mKd8WcY1bHs6JfA3eUg")).doesNotThrowAnyException();
    }
}
