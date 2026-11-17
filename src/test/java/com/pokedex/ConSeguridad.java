package com.pokedex;

import com.pokedex.config.ErroresSeguridad;
import com.pokedex.config.SeguridadConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Para los @WebMvcTest: carga las reglas de seguridad reales de la API (F15) en vez de las de
 * Spring Boot por defecto, con un secreto de JWT que solo existe en las pruebas (no es una
 * credencial: no firma nada fuera de ellas).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({SeguridadConfig.class, ErroresSeguridad.class})
@TestPropertySource(properties = "pokedex.seguridad.jwt-secreto=" + ConSeguridad.SECRETO_DE_PRUEBA)
public @interface ConSeguridad {

    String SECRETO_DE_PRUEBA = "secreto-solo-para-las-pruebas-automaticas-0123456789";
}
