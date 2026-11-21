package com.pokedex.service;

import com.pokedex.exception.LimiteExcedidoException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F20, tarea 2.3: 10 identificaciones por minuto por IP, con un reloj que se mueve a mano. */
class LimiteIdentificacionesTest {

    /** Reloj que solo avanza cuando la prueba lo manda. */
    static class RelojManual extends Clock {
        private long ms = 1_000_000L;

        void avanzar(long milisegundos) {
            ms += milisegundos;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(ms);
        }

        @Override
        public long millis() {
            return ms;
        }
    }

    private final RelojManual reloj = new RelojManual();
    private final LimiteIdentificaciones limite = new LimiteIdentificaciones(reloj);

    private void registrar(String ip, int veces) {
        for (int i = 0; i < veces; i++) {
            limite.registrar(ip);
        }
    }

    @Test
    void lasPrimeras10PasanYLaOnceEsRechazadaConSuMensaje() {
        registrar("1.1.1.1", 10);

        assertThatThrownBy(() -> limite.registrar("1.1.1.1"))
                .isInstanceOf(LimiteExcedidoException.class)
                .hasMessage("Demasiadas fotos seguidas. Espera un minuto");
    }

    @Test
    void laPeticion11EsRechazadaMientrasOtraIpSiguePasando() {
        registrar("1.1.1.1", 10);
        reloj.avanzar(30_000);

        assertThatThrownBy(() -> limite.registrar("1.1.1.1")).isInstanceOf(LimiteExcedidoException.class);
        assertThatCode(() -> limite.registrar("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void pasadoUnMinutoLaIpVuelveAPoderIdentificar() {
        registrar("1.1.1.1", 10);
        reloj.avanzar(LimiteIdentificaciones.VENTANA_MS);

        assertThatCode(() -> limite.registrar("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void laVentanaEsDeslizanteNoPorBloques() {
        registrar("1.1.1.1", 5);          // t = 0
        reloj.avanzar(40_000);
        registrar("1.1.1.1", 5);          // t = 40 s: ya van 10
        reloj.avanzar(21_000);            // t = 61 s: las 5 primeras salieron de la ventana

        registrar("1.1.1.1", 5);          // caben 5 más
        assertThatThrownBy(() -> limite.registrar("1.1.1.1")).isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void losIntentosRechazadosNoAlargaElBloqueo() {
        registrar("1.1.1.1", 10);
        for (int i = 0; i < 5; i++) {
            reloj.avanzar(5_000);
            assertThatThrownBy(() -> limite.registrar("1.1.1.1")).isInstanceOf(LimiteExcedidoException.class);
        }
        reloj.avanzar(35_000); // 60 s desde las 10 primeras

        assertThatCode(() -> limite.registrar("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void lasIpViejasSeBorranDeLaMemoria() {
        for (int i = 0; i < 500; i++) {
            limite.registrar("10.0.0." + i);
        }
        assertThat(limite.ipsEnMemoria()).isEqualTo(500);

        reloj.avanzar(LimiteIdentificaciones.VENTANA_MS + 1);
        limite.registrar("9.9.9.9"); // cualquier llamada dispara la limpieza

        assertThat(limite.ipsEnMemoria()).isEqualTo(1);
    }

    @Test
    void unaIpActivaNoSeBorraEnLaLimpieza() {
        limite.registrar("1.1.1.1");
        reloj.avanzar(50_000);
        limite.registrar("1.1.1.1");
        reloj.avanzar(15_000); // la limpieza corre, pero la última marca tiene 15 s

        limite.registrar("2.2.2.2");

        assertThat(limite.ipsEnMemoria()).isEqualTo(2);
    }
}
