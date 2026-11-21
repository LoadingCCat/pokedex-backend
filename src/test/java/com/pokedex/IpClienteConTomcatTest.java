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
 * F20, tarea 2.3: con {@code server.forward-headers-strategy=native} (el valor de
 * application.properties), Tomcat decide la IP del cliente y un {@code X-Forwarded-For} falso no
 * la cambia. Arranca Tomcat de verdad (MockMvc no pasa por la válvula {@code RemoteIpValve}).
 *
 * <p>Caso: un cliente cuya IP NO es un proxy interno (en producción, cualquier IP pública) manda
 * {@code X-Forwarded-For: 6.6.6.6}. Como desde la red local de la prueba no hay una IP pública,
 * se hace que {@code 127.0.0.1} no cuente como proxy interno.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EcoIp.class)
class IpClienteConTomcatTest {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry propiedades) {
        PostgresEmbebido.registrar(propiedades);
        propiedades.add("pokedex.seguridad.jwt-secreto", () -> ConSeguridad.SECRETO_DE_PRUEBA);
        propiedades.add("pokedex.seguridad.admin-usuario", () -> "admin-prueba");
        propiedades.add("pokedex.seguridad.admin-contrasena", () -> "clave-de-prueba");
        // 127.0.0.1 deja de ser un proxy interno: se comporta como un cliente de IP pública.
        propiedades.add("server.tomcat.remoteip.internal-proxies", () -> "10\\.99\\.99\\.99");
    }

    @LocalServerPort
    private int puerto;

    private String ipVistaPorElServidor(String xForwardedFor) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + "/api/prueba/ip"));
        if (xForwardedFor != null) {
            peticion.header("X-Forwarded-For", xForwardedFor);
        }
        HttpResponse<String> respuesta = HttpClient.newHttpClient()
                .send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return respuesta.body();
    }

    @Test
    void unXForwardedForFalsoDeUnClientePublicoNoCambiaLaIpQueCuenta() throws Exception {
        assertThat(ipVistaPorElServidor(null)).isEqualTo("127.0.0.1");
        assertThat(ipVistaPorElServidor("6.6.6.6")).isEqualTo("127.0.0.1");
        assertThat(ipVistaPorElServidor("6.6.6.6, 7.7.7.7")).isEqualTo("127.0.0.1");
    }
}
