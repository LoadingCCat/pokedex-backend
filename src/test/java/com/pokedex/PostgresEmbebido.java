package com.pokedex;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * PostgreSQL real (no H2) que arrancan las pruebas de consultas SQL: unaccent (F9) y las
 * subconsultas EXISTS de los filtros (F8) solo existen en PostgreSQL. Lo descarga Maven
 * (zonky embedded-postgres); no necesita Docker ni el PostgreSQL local encendido.
 * Se arranca una sola vez para todas las pruebas y se apaga al terminar la JVM.
 */
public final class PostgresEmbebido {

    private static EmbeddedPostgres servidor;

    private PostgresEmbebido() {
    }

    private static synchronized EmbeddedPostgres servidor() {
        if (servidor == null) {
            try {
                servidor = EmbeddedPostgres.start();
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo arrancar el PostgreSQL embebido", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    servidor.close();
                } catch (IOException ignorado) {
                    // la JVM ya está terminando
                }
            }));
        }
        return servidor;
    }

    /** Apunta el datasource de Spring al PostgreSQL embebido (reemplaza al de application-local). */
    public static void registrar(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", () -> servidor().getJdbcUrl("postgres", "postgres"));
        propiedades.add("spring.datasource.username", () -> "postgres");
        propiedades.add("spring.datasource.password", () -> "postgres");
    }
}
