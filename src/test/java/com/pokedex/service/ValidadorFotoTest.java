package com.pokedex.service;

import com.pokedex.exception.BadRequestException;
import com.pokedex.exception.FotoFaltanteException;
import com.pokedex.exception.FotoFormatoInvalidoException;
import com.pokedex.exception.FotoMuyPesadaException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F20, tarea 2.3: criterios de la foto (sin foto, vacía, formato, peso y válidas). */
class ValidadorFotoTest {

    private final ValidadorFoto validador = new ValidadorFoto();

    private static final byte[] CABECERA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] CABECERA_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] CABECERA_WEBP = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    private static MockMultipartFile foto(String nombre, String tipo, byte[] contenido) {
        return new MockMultipartFile("foto", nombre, tipo, contenido);
    }

    private static byte[] conRelleno(byte[] cabecera, int total) {
        byte[] datos = new byte[total];
        System.arraycopy(cabecera, 0, datos, 0, cabecera.length);
        return datos;
    }

    // ----- sin foto / vacía -----

    @Test
    void sinFotoEsFaltante() {
        assertThatThrownBy(() -> validador.validar(null))
                .isInstanceOf(FotoFaltanteException.class)
                .hasMessage("Envía una foto en el campo foto");
    }

    @Test
    void fotoVaciaEsFaltante() {
        assertThatThrownBy(() -> validador.validar(foto("a.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(FotoFaltanteException.class)
                .hasMessage("Envía una foto en el campo foto");
    }

    // ----- formato -----

    @Test
    void unPdfEsFormatoInvalido() {
        byte[] pdf = "%PDF-1.7\n1 0 obj\n<<>>\nendobj".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> validador.validar(foto("doc.pdf", "application/pdf", pdf)))
                .isInstanceOf(FotoFormatoInvalidoException.class)
                .hasMessage("La foto debe ser JPG, PNG o WEBP");
    }

    @Test
    void unTxtRenombradoAJpgEsFormatoInvalidoAunqueDigaImageJpeg() {
        byte[] texto = "esto no es una imagen, solo texto".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> validador.validar(foto("falsa.jpg", "image/jpeg", texto)))
                .isInstanceOf(FotoFormatoInvalidoException.class)
                .hasMessage("La foto debe ser JPG, PNG o WEBP");
    }

    @Test
    void unRiffQueNoEsWebpEsFormatoInvalido() {
        byte[] wav = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};

        assertThatThrownBy(() -> validador.validar(foto("a.webp", "image/webp", wav)))
                .isInstanceOf(FotoFormatoInvalidoException.class);
    }

    @Test
    void unArchivoMasCortoQueLaFirmaEsFormatoInvalido() {
        assertThatThrownBy(() -> validador.validar(foto("a.png", "image/png", new byte[]{(byte) 0x89, 'P'})))
                .isInstanceOf(FotoFormatoInvalidoException.class);
    }

    // ----- peso -----

    @Test
    void unaFotoDeMasDe5MbEsMuyPesadaAunqueSeaJpegValido() {
        byte[] grande = conRelleno(CABECERA_JPEG, 5 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> validador.validar(foto("grande.jpg", "image/jpeg", grande)))
                .isInstanceOf(FotoMuyPesadaException.class)
                .hasMessage("La foto pesa más de 5 MB");
    }

    @Test
    void unaFotoDeExactamente5MbPasa() {
        byte[] justa = conRelleno(CABECERA_JPEG, 5 * 1024 * 1024);

        assertThat(validador.validar(foto("justa.jpg", "image/jpeg", justa))).isEqualTo("image/jpeg");
    }

    // ----- válidas -----

    @Test
    void unJpegValidPasa() {
        assertThat(validador.validar(foto("a.jpg", "image/jpeg", CABECERA_JPEG))).isEqualTo("image/jpeg");
    }

    @Test
    void unPngValidoPasa() {
        assertThat(validador.validar(foto("a.png", "image/png", CABECERA_PNG))).isEqualTo("image/png");
    }

    @Test
    void unWebpValidoPasa() {
        assertThat(validador.validar(foto("a.webp", "image/webp", CABECERA_WEBP))).isEqualTo("image/webp");
    }

    @Test
    void elTipoLoDecidenLosBytesNoElContentTypeNiLaExtension() {
        assertThat(validador.validar(foto("sin-extension", "text/plain", CABECERA_PNG))).isEqualTo("image/png");
    }

    // ----- los errores de foto son 400 -----

    @Test
    void lasTresExcepcionesDeFotoSon400() {
        assertThat(new FotoFaltanteException()).isInstanceOf(BadRequestException.class);
        assertThat(new FotoFormatoInvalidoException()).isInstanceOf(BadRequestException.class);
        assertThat(new FotoMuyPesadaException()).isInstanceOf(BadRequestException.class);
    }
}
