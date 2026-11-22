package com.pokedex.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * N-01 (interventoría 4.3): el frontend llama al backend desde su servidor, así que la IP de la
 * conexión es la de Vercel. {@link IpCliente} usa la IP real que el frontend declara en
 * X-Cliente-IP solo si X-Proxy-Secreto coincide con PROXY_SECRETO; en cualquier otro caso,
 * la de la conexión.
 */
class IpClienteTest {

    private static final String SECRETO = "secreto-del-proxy-0123456789";

    private static MockHttpServletRequest peticion(String ipConexion, String ipDeclarada, String secreto) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ipConexion);
        if (ipDeclarada != null) {
            request.addHeader(IpCliente.CABECERA_IP, ipDeclarada);
        }
        if (secreto != null) {
            request.addHeader(IpCliente.CABECERA_SECRETO, secreto);
        }
        return request;
    }

    @Test
    void conElSecretoCorrectoUsaLaIpDeclaradaPorElProxy() {
        IpCliente ipCliente = new IpCliente(SECRETO);

        assertThat(ipCliente.de(peticion("76.76.21.1", "203.0.113.7", SECRETO))).isEqualTo("203.0.113.7");
    }

    @Test
    void conUnSecretoMaloIgnoraLaIpDeclarada() {
        IpCliente ipCliente = new IpCliente(SECRETO);

        assertThat(ipCliente.de(peticion("76.76.21.1", "203.0.113.7", "otro-secreto-cualquiera-9876"))).isEqualTo("76.76.21.1");
    }

    @Test
    void sinCabeceraDeSecretoIgnoraLaIpDeclarada() {
        IpCliente ipCliente = new IpCliente(SECRETO);

        assertThat(ipCliente.de(peticion("76.76.21.1", "203.0.113.7", null))).isEqualTo("76.76.21.1");
    }

    @Test
    void conUnSecretoDeOtroLargoTambienLoIgnora() {
        IpCliente ipCliente = new IpCliente(SECRETO);

        assertThat(ipCliente.de(peticion("76.76.21.1", "203.0.113.7", SECRETO + "x"))).isEqualTo("76.76.21.1");
        assertThat(ipCliente.de(peticion("76.76.21.1", "203.0.113.7", ""))).isEqualTo("76.76.21.1");
    }

    @Test
    void sinPROXY_SECRETOSeComportaComoAntesAunqueLleguenLasCabeceras() {
        for (String sinSecreto : new String[]{"", "   ", null}) {
            IpCliente ipCliente = new IpCliente(sinSecreto);

            assertThat(ipCliente.de(peticion("76.76.21.1", "203.0.113.7", "cualquier-cosa-aqui-1234"))).isEqualTo("76.76.21.1");
            assertThat(ipCliente.de(peticion("76.76.21.1", "203.0.113.7", ""))).isEqualTo("76.76.21.1");
        }
    }

    @Test
    void conElSecretoCorrectoPeroSinIpDeclaradaUsaLaDeLaConexion() {
        IpCliente ipCliente = new IpCliente(SECRETO);

        assertThat(ipCliente.de(peticion("76.76.21.1", null, SECRETO))).isEqualTo("76.76.21.1");
        assertThat(ipCliente.de(peticion("76.76.21.1", "  ", SECRETO))).isEqualTo("76.76.21.1");
    }

    @Test
    void unaIpDeclaradaQueNoEsUnaIpSeIgnoraAunqueElSecretoSeaCorrecto() {
        IpCliente ipCliente = new IpCliente(SECRETO);

        assertThat(ipCliente.de(peticion("76.76.21.1", "no-es-una-ip", SECRETO))).isEqualTo("76.76.21.1");
        assertThat(ipCliente.de(peticion("76.76.21.1", "1.2.3.4, 5.6.7.8", SECRETO))).isEqualTo("76.76.21.1");
        assertThat(ipCliente.de(peticion("76.76.21.1", "a".repeat(200), SECRETO))).isEqualTo("76.76.21.1");
    }

    @Test
    void aceptaUnaIpV6() {
        IpCliente ipCliente = new IpCliente(SECRETO);

        assertThat(ipCliente.de(peticion("76.76.21.1", "2001:db8::1", SECRETO))).isEqualTo("2001:db8::1");
    }

    @Test
    void unSecretoConfiguradoMuyCortoImpideArrancar() {
        assertThatThrownBy(() -> new IpCliente("corto"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROXY_SECRETO");
    }
}
