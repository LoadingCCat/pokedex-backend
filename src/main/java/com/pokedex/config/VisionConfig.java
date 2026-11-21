package com.pokedex.config;

import com.pokedex.service.ServicioVision;
import com.pokedex.service.ServicioVisionGemini;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * F20: el servicio de visión real. Con el perfil {@code e2e} no se crea: lo reemplaza
 * {@code ServicioVisionMock}. Sin GEMINI_API_KEY el servidor arranca igual; la identificación
 * falla después con {@code VisionNoDisponibleException}.
 */
@Configuration
@Profile("!e2e")
public class VisionConfig {

    private static final String URL_BASE = "https://generativelanguage.googleapis.com/v1beta";

    @Bean
    public ServicioVision servicioVision(
            @Value("${pokedex.vision.gemini-api-key:}") String clave,
            @Value("${pokedex.vision.gemini-modelo:gemini-3.5-flash-lite}") String modelo) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(20));
        RestClient cliente = RestClient.builder()
                .baseUrl(URL_BASE)
                .requestFactory(fabrica)
                .build();
        return new ServicioVisionGemini(cliente, clave, modelo);
    }
}
