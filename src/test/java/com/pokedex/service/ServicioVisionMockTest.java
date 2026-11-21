package com.pokedex.service;

import com.pokedex.exception.VisionNoDisponibleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F20, tarea 2.2: el mock responde lo que se le configure, para las pruebas y el perfil e2e. */
class ServicioVisionMockTest {

    private static final byte[] FOTO = {1};

    @Test
    void porDefectoReconoceAUnCharmander() {
        ResultadoVision resultado = new ServicioVisionMock().identificar(FOTO, "image/png");

        assertThat(resultado).isEqualTo(new ResultadoVision(true, "charmander", 0.93));
    }

    @Test
    void respondeLoQueSeLeConfigureYCuentaLasLlamadas() {
        ServicioVisionMock mock = new ServicioVisionMock();
        mock.responder(new ResultadoVision(false, null, 0.2));

        assertThat(mock.identificar(FOTO, "image/png")).isEqualTo(new ResultadoVision(false, null, 0.2));
        assertThat(mock.llamadas()).isEqualTo(1);
    }

    @Test
    void puedeSimularUnaFalla() {
        ServicioVisionMock mock = new ServicioVisionMock();
        mock.fallar();

        assertThatThrownBy(() -> mock.identificar(FOTO, "image/png"))
                .isInstanceOf(VisionNoDisponibleException.class);
    }
}
