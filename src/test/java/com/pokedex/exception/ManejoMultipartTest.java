package com.pokedex.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F20: las excepciones de multipart que lanza Spring (antes del controller) dan 400 con el
 * mensaje del contrato, nunca el 500 genérico. MockMvc no aplica los límites de tamaño del
 * servidor, así que estas tres se comprueban llamando al manejador directamente.
 */
class ManejoMultipartTest {

    private final GlobalExceptionHandler manejador = new GlobalExceptionHandler();
    private final MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/pokemons/identificar");

    @Test
    void faltaLaParteFotoDa400() {
        ResponseEntity<Map<String, Object>> r =
                manejador.manejarMultipartInvalido(new MissingServletRequestPartException("foto"), peticion);

        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody()).containsEntry("mensaje", "Envía una foto en el campo foto");
    }

    @Test
    void multipartMalFormadoDa400SinFiltrarElDetalle() {
        ResponseEntity<Map<String, Object>> r = manejador.manejarMultipartInvalido(
                new MultipartException("detalle interno: C:\\tmp\\upload_123.tmp"), peticion);

        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody()).containsEntry("mensaje", "Envía una foto en el campo foto");
        assertThat(r.getBody().toString()).doesNotContain("detalle interno").doesNotContain("upload_123");
    }

    @Test
    void subidaMasGrandeQueElLimiteDa400() {
        ResponseEntity<Map<String, Object>> r = manejador.manejarSubidaMuyGrande(
                new MaxUploadSizeExceededException(6L * 1024 * 1024), peticion);

        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody()).containsEntry("mensaje", "La foto pesa más de 5 MB");
    }
}
