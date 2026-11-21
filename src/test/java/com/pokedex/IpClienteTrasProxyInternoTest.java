package com.pokedex;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F20, tarea 2.3: con la configuración por defecto de {@code internal-proxies}, una conexión desde
 * un proxy interno (aquí, el loopback) sí se cree la cabecera, pero de derecha a izquierda: la IP
 * que cuenta es la primera que no es un proxy interno, nunca la entrada escrita por el cliente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EcoIp.class)
class IpClienteTrasProxyInternoTest {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry propiedades) {
        PostgresEmbebido.registrar(propiedades);
        propiedades.add("pokedex.seguridad.jwt-secreto", () -> ConSeguridad.SECRETO_DE_PRUEBA);
        propiedades.add("pokedex.seguridad.admin-usuario", () -> "admin-prueba");
        propiedades.add("pokedex.seguridad.admin-contrasena", () -> "clave-de-prueba");
    }

    @LocalServerPort
    private int puerto;

    private String ipVistaPorElServidor(String xForwardedFor) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/api/prueba/ip"))
                .header("X-Forwarded-For", xForwardedFor)
                .build();
        HttpResponse<String> respuesta = HttpClient.newHttpClient()
                .send(peticion, HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return respuesta.body();
    }

    @Test
    void conUnProxyInternoCuentaLaIpDelClienteRealYNoLaPrimeraEntradaFalsa() throws Exception {
        // El cliente escribió 6.6.6.6; el proxy agregó la IP real 203.0.113.9.
        assertThat(ipVistaPorElServidor("6.6.6.6, 203.0.113.9")).isEqualTo("203.0.113.9");
    }

    @Test
    void variasEntradasFalsasANuestraIzquierdaTampocoCuentan() throws Exception {
        assertThat(ipVistaPorElServidor("6.6.6.6, 7.7.7.7, 203.0.113.9, 10.1.2.3")).isEqualTo("203.0.113.9");
    }
}
