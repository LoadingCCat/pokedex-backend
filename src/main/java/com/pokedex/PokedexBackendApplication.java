package com.pokedex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@SpringBootApplication
public class PokedexBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PokedexBackendApplication.class, args);
    }

    /**
     * Cliente HTTP para la PokeAPI con tiempos límite: sin ellos, si la PokeAPI deja
     * de responder en una sola llamada, la importación se queda esperando para siempre.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));  // tiempo para conectarse
        fabrica.setReadTimeout(Duration.ofSeconds(15));    // tiempo máximo esperando la respuesta
        return new RestTemplate(fabrica);
    }
}
