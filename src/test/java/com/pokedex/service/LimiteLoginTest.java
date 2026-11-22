package com.pokedex.service;

import com.pokedex.exception.LimiteExcedidoException;
import com.pokedex.service.LimiteIdentificacionesTest.RelojManual;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S-01 (interventoría): como máximo 5 intentos de login fallidos por minuto por IP, contados en
 * memoria con una ventana deslizante, igual que LimiteIdentificaciones (F20). Solo cuentan los
 * fallos: un login correcto no consume cupo.
 */
class LimiteLoginTest {

    private final RelojManual reloj = new RelojManual();
    private final LimiteLogin limite = new LimiteLogin(reloj);

    private void fallar(String ip, int veces) {
        for (int i = 0; i < veces; i++) {
            limite.verificar(ip);
            limite.registrarFallo(ip);
        }
    }

    @Test
    void loscincoPrimerosFallosPasanYElSextoIntentoEsRechazadoConSuMensaje() {
        fallar("1.1.1.1", 5);

        assertThatThrownBy(() -> limite.verificar("1.1.1.1"))
                .isInstanceOf(LimiteExcedidoException.class)
                .hasMessage("Demasiados intentos de inicio de sesión. Espera un minuto");
    }

    @Test
    void sinFallosNuncaSeBloquea() {
        // Los logins correctos solo llaman a verificar(): no consumen cupo
        for (int i = 0; i < 50; i++) {
            limite.verificar("1.1.1.1");
        }

        assertThatCode(() -> limite.verificar("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void otraIpSigueTeniendoSuCupo() {
        fallar("1.1.1.1", 5);

        assertThatCode(() -> limite.verificar("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void pasadoUnMinutoLaIpPuedeVolverAIntentar() {
        fallar("1.1.1.1", 5);
        reloj.avanzar(LimiteLogin.VENTANA_MS);

        assertThatCode(() -> limite.verificar("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void laVentanaEsDeslizanteNoPorBloques() {
        fallar("1.1.1.1", 3);       // t = 0
        reloj.avanzar(40_000);
        fallar("1.1.1.1", 2);       // t = 40 s: ya van 5
        assertThatThrownBy(() -> limite.verificar("1.1.1.1")).isInstanceOf(LimiteExcedidoException.class);

        reloj.avanzar(21_000);      // t = 61 s: los 3 primeros salieron de la ventana
        fallar("1.1.1.1", 3);       // caben 3 más (quedan 2 dentro de la ventana)
        assertThatThrownBy(() -> limite.verificar("1.1.1.1")).isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void losIntentosRechazadosNoAlarganElBloqueo() {
        fallar("1.1.1.1", 5);
        for (int i = 0; i < 5; i++) {
            reloj.avanzar(5_000);
            assertThatThrownBy(() -> limite.verificar("1.1.1.1")).isInstanceOf(LimiteExcedidoException.class);
        }
        reloj.avanzar(35_000); // 60 s desde los 5 primeros fallos

        assertThatCode(() -> limite.verificar("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void lasIpViejasSeBorranDeLaMemoria() {
        for (int i = 0; i < 500; i++) {
            limite.registrarFallo("10.0.0." + i);
        }
        assertThat(limite.ipsEnMemoria()).isEqualTo(500);

        reloj.avanzar(LimiteLogin.VENTANA_MS + 1);
        limite.registrarFallo("9.9.9.9"); // cualquier llamada dispara la limpieza

        assertThat(limite.ipsEnMemoria()).isEqualTo(1);
    }
}
