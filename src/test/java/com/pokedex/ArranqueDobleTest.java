package com.pokedex;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-09 (interventoría 4.2) · criterio de F9: «el servidor arranca dos veces seguidas sin error
 * (crear la extensión es idempotente)». Usa su propio PostgreSQL embebido, recién creado, para
 * que el primer arranque cree {@code unaccent} de verdad y el segundo se encuentre con ella.
 */
class ArranqueDobleTest {

    private ConfigurableApplicationContext arrancar(EmbeddedPostgres bd) {
        return SpringApplication.run(PokedexBackendApplication.class,
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--spring.datasource.url=" + bd.getJdbcUrl("postgres", "postgres"),
                "--spring.datasource.username=postgres",
                "--spring.datasource.password=postgres",
                "--pokedex.seguridad.jwt-secreto=" + ConSeguridad.SECRETO_DE_PRUEBA,
                "--pokedex.seguridad.admin-usuario=admin-prueba",
                "--pokedex.seguridad.admin-contrasena=clave-de-prueba");
    }

    private int extensionesUnaccent(ConfigurableApplicationContext contexto) throws Exception {
        DataSource origen = contexto.getBean(DataSource.class);
        try (Connection conexion = origen.getConnection();
             Statement consulta = conexion.createStatement();
             ResultSet filas = consulta.executeQuery("SELECT count(*) FROM pg_extension WHERE extname = 'unaccent'")) {
            filas.next();
            return filas.getInt(1);
        }
    }

    @Test
    void elServidorArrancaDosVecesSeguidasSinErrorYLaExtensionQuedaUnaVez() throws Exception {
        try (EmbeddedPostgres bd = EmbeddedPostgres.start()) {
            try (ConfigurableApplicationContext primero = arrancar(bd)) {
                assertThat(primero.isRunning()).isTrue();
                assertThat(extensionesUnaccent(primero)).isEqualTo(1);
            }
            try (ConfigurableApplicationContext segundo = arrancar(bd)) {
                assertThat(segundo.isRunning()).isTrue();
                assertThat(extensionesUnaccent(segundo)).isEqualTo(1);
            }
        }
    }
}
