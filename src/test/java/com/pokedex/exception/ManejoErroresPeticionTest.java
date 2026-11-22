package com.pokedex.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-03 y S-04 (interventoría): errores de la petición que Spring lanza antes o después del
 * controller. El manejador se llama directamente, como en ManejoMultipartTest.
 */
class ManejoErroresPeticionTest {

    private final GlobalExceptionHandler manejador = new GlobalExceptionHandler();
    private final MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/pokemons");

    /** Spring Data la lanza con el nombre de la clase y del campo; el manejador la busca por su nombre simple. */
    static class PropertyReferenceException extends RuntimeException {
        PropertyReferenceException() {
            super("No property 'foo' found for type 'Pokemon'");
        }
    }

    // ---------- S-03 · el 400 de sort no filtra nombres internos ----------

    @Test
    void sortInvalidoDa400SinElNombreDeLaClaseNiDelTipo() {
        peticion.setParameter("sort", "foo,asc");

        ResponseEntity<Map<String, Object>> r =
                manejador.manejarErroresGlobales(new PropertyReferenceException(), peticion);

        assertThat(r.getStatusCode().value()).isEqualTo(400);
        String mensaje = (String) r.getBody().get("mensaje");
        assertThat(mensaje).startsWith("El parámetro 'sort' no es válido").contains("'foo'");
        assertThat(mensaje).doesNotContain("Pokemon").doesNotContain("type").doesNotContain("No property");
    }

    @Test
    void sortInvalidoEnvueltoEnOtraExcepcionTambienDa400ConMensajeFijo() {
        peticion.setParameter("sort", "foo");

        ResponseEntity<Map<String, Object>> r = manejador.manejarErroresGlobales(
                new IllegalStateException("fallo de consulta", new PropertyReferenceException()), peticion);

        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat((String) r.getBody().get("mensaje")).doesNotContain("Pokemon").doesNotContain("fallo de consulta");
    }

    @Test
    void sinElParametroSortElMensajeSigueSiendoFijo() {
        ResponseEntity<Map<String, Object>> r =
                manejador.manejarErroresGlobales(new PropertyReferenceException(), peticion);

        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(r.getBody()).containsEntry("mensaje", "El parámetro 'sort' no es válido");
    }

    // ---------- S-04 · 415 y 406 ----------

    @Test
    void contentTypeNoSoportadoDa415ConElFormatoDeError() {
        ResponseEntity<Map<String, Object>> r = manejador.manejarTipoDeContenidoNoSoportado(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                peticion);

        assertThat(r.getStatusCode().value()).isEqualTo(415);
        assertThat(r.getBody()).containsKeys("timestamp", "status", "error", "mensaje", "path");
        assertThat(r.getBody()).containsEntry("status", 415).containsEntry("path", "/api/pokemons");
    }

    @Test
    void acceptQueLaApiNoPuedeCumplirDa406ConElFormatoDeError() {
        ResponseEntity<Map<String, Object>> r = manejador.manejarTipoDeRespuestaNoAceptable(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), peticion);

        assertThat(r.getStatusCode().value()).isEqualTo(406);
        assertThat(r.getBody()).containsKeys("timestamp", "status", "error", "mensaje", "path");
        assertThat(r.getBody()).containsEntry("status", 406);
    }
}
