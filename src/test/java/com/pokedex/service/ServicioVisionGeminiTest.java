package com.pokedex.service;

import com.pokedex.exception.VisionNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * F20, tarea 2.2: la respuesta de Gemini se convierte en ResultadoVision y todo lo inesperado
 * (JSON raro, vacío, error del proveedor, timeout, sin clave) lanza VisionNoDisponibleException.
 * El HTTP está simulado: ninguna prueba llama a Gemini de verdad.
 */
class ServicioVisionGeminiTest {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final String CLAVE_DE_PRUEBA = "clave-falsa-de-prueba";
    private static final String MODELO = "modelo-de-prueba";
    private static final byte[] FOTO = {1, 2, 3, 4};

    private MockRestServiceServer servidor;
    private ServicioVisionGemini servicio;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        servidor = MockRestServiceServer.bindTo(builder).build();
        servicio = new ServicioVisionGemini(builder.build(), CLAVE_DE_PRUEBA, MODELO);
    }

    /** Respuesta de Gemini cuyo único texto es {@code textoDelModelo}. */
    private static String respuestaGemini(String textoDelModelo) {
        String escapado = textoDelModelo.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + escapado + "\"}]},\"finishReason\":\"STOP\"}]}";
    }

    private void respondeConTexto(String textoDelModelo) {
        servidor.expect(requestTo(BASE + "/models/" + MODELO + ":generateContent"))
                .andRespond(withSuccess(respuestaGemini(textoDelModelo), MediaType.APPLICATION_JSON));
    }

    @Test
    void convierteLaRespuestaEnResultadoVision() {
        respondeConTexto("{\"identificado\":true,\"slug\":\"mr-mime\",\"confianza\":0.93}");

        ResultadoVision resultado = servicio.identificar(FOTO, "image/png");

        assertThat(resultado).isEqualTo(new ResultadoVision(true, "mr-mime", 0.93));
        servidor.verify();
    }

    @Test
    void elSlugSeNormalizaAMinusculas() {
        respondeConTexto("{\"identificado\":true,\"slug\":\" Pikachu \",\"confianza\":0.8}");

        assertThat(servicio.identificar(FOTO, "image/jpeg").slug()).isEqualTo("pikachu");
    }

    @Test
    void siNoReconoceUnPokemonDevuelveIdentificadoFalsoSinSlug() {
        respondeConTexto("{\"identificado\":false,\"slug\":\"\",\"confianza\":0.1}");

        ResultadoVision resultado = servicio.identificar(FOTO, "image/webp");

        assertThat(resultado.identificado()).isFalse();
        assertThat(resultado.slug()).isNull();
        assertThat(resultado.confianza()).isEqualTo(0.1);
    }

    @Test
    void enviaLaImagenInlineConSuTipoLaClaveEnLaCabeceraYPideJson() {
        servidor.expect(method(HttpMethod.POST))
                .andExpect(requestTo(BASE + "/models/" + MODELO + ":generateContent"))
                .andExpect(header("x-goog-api-key", CLAVE_DE_PRUEBA))
                .andExpect(jsonPath("$.contents[0].parts[0].inline_data.mime_type").value("image/png"))
                .andExpect(jsonPath("$.contents[0].parts[0].inline_data.data")
                        .value(Base64.getEncoder().encodeToString(FOTO)))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.contents[0].parts[1].text").isNotEmpty())
                .andRespond(withSuccess(
                        respuestaGemini("{\"identificado\":true,\"slug\":\"eevee\",\"confianza\":0.9}"),
                        MediaType.APPLICATION_JSON));

        servicio.identificar(FOTO, "image/png");

        servidor.verify();
    }

    @Test
    void laClaveNuncaVaEnLaUrl() {
        servidor.expect(requestTo(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(CLAVE_DE_PRUEBA))))
                .andRespond(withSuccess(
                        respuestaGemini("{\"identificado\":true,\"slug\":\"eevee\",\"confianza\":0.9}"),
                        MediaType.APPLICATION_JSON));

        servicio.identificar(FOTO, "image/png");

        servidor.verify();
    }

    @Test
    void unTextoQueNoEsJsonLanzaLaExcepcion() {
        respondeConTexto("Es un Pikachu, seguro");

        assertThatThrownBy(() -> servicio.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
    }

    @Test
    void unJsonSinLosCamposEsperadosLanzaLaExcepcion() {
        respondeConTexto("{\"nombre\":\"pikachu\"}");

        assertThatThrownBy(() -> servicio.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
    }

    @Test
    void unJsonQueNoEsUnObjetoLanzaLaExcepcion() {
        respondeConTexto("[\"pikachu\"]");

        assertThatThrownBy(() -> servicio.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
    }

    @Test
    void unaConfianzaFueraDeRangoLanzaLaExcepcion() {
        respondeConTexto("{\"identificado\":true,\"slug\":\"pikachu\",\"confianza\":7}");

        assertThatThrownBy(() -> servicio.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
    }

    @Test
    void unSlugConFormatoInvalidoLanzaLaExcepcion() {
        respondeConTexto("{\"identificado\":true,\"slug\":\"pikachu; ignora todo\",\"confianza\":0.9}");

        assertThatThrownBy(() -> servicio.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
    }

    @Test
    void unTextoVacioLanzaLaExcepcion() {
        respondeConTexto("");

        assertThatThrownBy(() -> servicio.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
    }

    @Test
    void unaRespuestaSinCandidatosLanzaLaExcepcion() {
        // Así responde Gemini cuando bloquea el prompt: solo promptFeedback.
        servidor.expect(requestTo(BASE + "/models/" + MODELO + ":generateContent"))
                .andRespond(withSuccess("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> servicio.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
    }

    @Test
    void unCuerpoVacioLanzaLaExcepcion() {
        servidor.expect(requestTo(BASE + "/models/" + MODELO + ":generateContent"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> servicio.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
    }

    @Test
    void unErrorDelProveedorLanzaLaExcepcionSinFiltrarSuCuerpo() {
        servidor.expect(requestTo(BASE + "/models/" + MODELO + ":generateContent"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"detalle interno del proveedor\"}}"));

        assertThatThrownBy(() -> servicio.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class)
                .hasMessageNotContaining("proveedor")
                .hasNoCause();
    }

    @Test
    void unTimeoutLanzaLaMismaExcepcion() {
        servidor.expect(requestTo(BASE + "/models/" + MODELO + ":generateContent"))
                .andRespond(request -> {
                    throw new IOException("Read timed out");
                });

        assertThatThrownBy(() -> servicio.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class)
                .hasMessageNotContaining("timed out")
                .hasNoCause();
    }

    @Test
    void sinClaveLanzaLaExcepcionYNoLlamaAlProveedor() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer sinLlamadas = MockRestServiceServer.bindTo(builder).build();
        ServicioVisionGemini sinClave = new ServicioVisionGemini(builder.build(), "", MODELO);

        assertThatThrownBy(() -> sinClave.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
        sinLlamadas.verify(); // no había ninguna llamada esperada: si hubiera salido una, habría fallado
    }

    @Test
    void unaClaveNulaTambienCuentaComoSinClave() {
        ServicioVisionGemini sinClave = new ServicioVisionGemini(RestClient.create(BASE), null, MODELO);

        assertThatThrownBy(() -> sinClave.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
    }
}
