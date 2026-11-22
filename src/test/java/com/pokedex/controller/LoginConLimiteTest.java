package com.pokedex.controller;

import com.pokedex.ConSeguridad;
import com.pokedex.entity.Usuario;
import com.pokedex.repository.UsuarioRepository;
import com.pokedex.service.IpCliente;
import com.pokedex.service.LimiteLogin;
import com.pokedex.service.ServicioToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S-01, S-04 (interventoría): el login limita los intentos fallidos por IP (429), topa el largo
 * del usuario y de la contraseña (400 con `campos`) y responde 415 a un Content-Type que no es
 * JSON. El limitador vive en memoria y el contexto se comparte entre pruebas, así que cada
 * prueba usa su propia IP.
 */
@WebMvcTest(AuthController.class)
@ConSeguridad
@Import({ServicioToken.class, LimiteLogin.class, IpCliente.class})
@TestPropertySource(properties = "pokedex.seguridad.proxy-secreto=" + LoginConLimiteTest.PROXY_SECRETO)
class LoginConLimiteTest {

    static final String PROXY_SECRETO = "secreto-del-proxy-0123456789";
    private static final String MALA = """
            {"usuario": "admin", "contrasena": "incorrecta"}""";
    private static final String BUENA = """
            {"usuario": "admin", "contrasena": "clave-de-prueba"}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PasswordEncoder cifrador;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    private void existeAdmin() {
        when(usuarioRepository.findByUsuario("admin"))
                .thenReturn(Optional.of(new Usuario("admin", cifrador.encode("clave-de-prueba"), Usuario.ROL_ADMIN)));
    }

    private ResultActions login(String ip, String json) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json)
                .with(peticion -> {
                    peticion.setRemoteAddr(ip);
                    return peticion;
                }));
    }

    // ---------- S-01 · límite de intentos ----------

    @Test
    void elSextoIntentoFallidoDeLaMismaIpResponde429ConUnMensajeClaro() throws Exception {
        existeAdmin();
        for (int i = 0; i < 5; i++) {
            login("10.1.0.1", MALA).andExpect(status().isUnauthorized());
        }

        login("10.1.0.1", MALA)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.mensaje").value("Demasiados intentos de inicio de sesión. Espera un minuto"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void conLaIpBloqueadaNiLaContrasenaCorrectaPasaYNoSeConsultaLaBase() throws Exception {
        existeAdmin();
        for (int i = 0; i < 5; i++) {
            login("10.1.0.2", MALA).andExpect(status().isUnauthorized());
        }

        login("10.1.0.2", BUENA).andExpect(status().isTooManyRequests());

        // Solo las 5 consultas de los fallos: el sexto intento se corta antes de tocar la BD y BCrypt
        verify(usuarioRepository, times(5)).findByUsuario("admin");
    }

    @Test
    void otraIpNoSeBloqueaPorLosFallosDeUnaIpAjena() throws Exception {
        existeAdmin();
        for (int i = 0; i < 6; i++) {
            login("10.1.0.3", MALA);
        }

        login("10.1.0.4", BUENA).andExpect(status().isOk());
    }

    @Test
    void losLoginsCorrectosNoConsumenCupo() throws Exception {
        existeAdmin();
        for (int i = 0; i < 12; i++) {
            login("10.1.0.5", BUENA).andExpect(status().isOk());
        }

        login("10.1.0.5", MALA).andExpect(status().isUnauthorized());
    }

    @Test
    void lasPeticionesInvalidasNoCuentanComoIntentoFallido() throws Exception {
        existeAdmin();
        for (int i = 0; i < 8; i++) {
            login("10.1.0.6", """
                    {"usuario": "", "contrasena": "x"}""").andExpect(status().isBadRequest());
        }

        login("10.1.0.6", BUENA).andExpect(status().isOk());
    }

    // ---------- S-01 · largo del usuario y de la contraseña ----------

    @Test
    void unUsuarioDeMasDe100CaracteresResponde400ConSuCampo() throws Exception {
        String largo = "u".repeat(101);

        login("10.1.0.7", "{\"usuario\": \"" + largo + "\", \"contrasena\": \"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.usuario").exists());
        verify(usuarioRepository, never()).findByUsuario(anyString());
    }

    @Test
    void unaContrasenaDeMasDe128CaracteresResponde400ConSuCampo() throws Exception {
        String larga = "c".repeat(129);

        login("10.1.0.8", "{\"usuario\": \"admin\", \"contrasena\": \"" + larga + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.contrasena").exists());
        verify(usuarioRepository, never()).findByUsuario(anyString());
    }

    @Test
    void enElLimiteExactoDeLargoLaPeticionSiLlegaALaVerificacion() throws Exception {
        when(usuarioRepository.findByUsuario("u".repeat(100))).thenReturn(Optional.empty());

        login("10.1.0.9", "{\"usuario\": \"" + "u".repeat(100) + "\", \"contrasena\": \"" + "c".repeat(128) + "\"}")
                .andExpect(status().isUnauthorized());
    }

    // ---------- N-01 · IP real detrás del proxy de confianza (el servidor de Next) ----------

    /** Como el Route Handler de Next: la conexión es siempre la del proxy y la IP real va en cabeceras. */
    private ResultActions loginDesdeElProxy(String ipReal, String secreto, String json) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json)
                .header("X-Cliente-IP", ipReal)
                .header("X-Proxy-Secreto", secreto)
                .with(peticion -> {
                    peticion.setRemoteAddr("10.2.0.1");
                    return peticion;
                }));
    }

    @Test
    void dosClientesDetrasDelMismoProxyTienenCuposSeparados() throws Exception {
        existeAdmin();
        for (int i = 0; i < 5; i++) {
            loginDesdeElProxy("203.0.113.10", PROXY_SECRETO, MALA).andExpect(status().isUnauthorized());
        }
        loginDesdeElProxy("203.0.113.10", PROXY_SECRETO, MALA).andExpect(status().isTooManyRequests());

        // otro cliente detrás del mismo proxy no está bloqueado y puede entrar
        loginDesdeElProxy("203.0.113.11", PROXY_SECRETO, BUENA).andExpect(status().isOk());
    }

    @Test
    void conUnSecretoMaloLaCabeceraSeIgnoraYTodosCompartenLaIpDeLaConexion() throws Exception {
        existeAdmin();
        for (int i = 0; i < 5; i++) {
            // cada intento dice ser otro cliente, pero el secreto no vale: cuenta la IP de la conexión
            loginDesdeElProxy("198.51.100." + i, "secreto-falso-0123456789", MALA).andExpect(status().isUnauthorized());
        }

        loginDesdeElProxy("198.51.100.99", "secreto-falso-0123456789", MALA).andExpect(status().isTooManyRequests());
    }

    // ---------- N-03 · Retry-After ----------

    @Test
    void el429DelLoginLlevaRetryAfterDeUnMinuto() throws Exception {
        existeAdmin();
        for (int i = 0; i < 5; i++) {
            login("10.1.0.20", MALA).andExpect(status().isUnauthorized());
        }

        login("10.1.0.20", MALA)
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "60"));
    }

    // ---------- S-04 · Content-Type que no es JSON ----------

    @Test
    void unContentTypeQueNoEsJsonResponde415ConElFormatoDeError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("usuario=admin")
                        .with(peticion -> {
                            peticion.setRemoteAddr("10.1.0.10");
                            return peticion;
                        }))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Tipo de contenido no soportado"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }
}
