package com.pokedex.controller;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pokedex.ConSeguridad;
import com.pokedex.entity.Movimiento;
import com.pokedex.entity.Usuario;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.UsuarioRepository;
import com.pokedex.service.IpCliente;
import com.pokedex.service.LimiteLogin;
import com.pokedex.service.ServicioToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de F15 sobre el login, los 401/403 y los GET públicos, con las reglas reales. */
@WebMvcTest(controllers = {AuthController.class, MovimientoController.class})
@ConSeguridad
@Import({ServicioToken.class, LimiteLogin.class, IpCliente.class})
class SeguridadControllerTest {

    private static final String MOVIMIENTO = """
            {"nombre": "rayo-prueba", "categoria": "Especial", "potencia": 90}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PasswordEncoder cifrador;
    @Autowired
    private ServicioToken servicioToken;

    @MockitoBean
    private UsuarioRepository usuarioRepository;
    @MockitoBean
    private MovimientoRepository movimientoRepository;

    private void existeAdmin(String usuario, String contrasena) {
        when(usuarioRepository.findByUsuario(usuario))
                .thenReturn(Optional.of(new Usuario(usuario, cifrador.encode(contrasena), Usuario.ROL_ADMIN)));
    }

    private ResultActions login(String json) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions crearMovimiento(String token) throws Exception {
        var peticion = post("/api/movimientos").contentType(MediaType.APPLICATION_JSON).content(MOVIMIENTO);
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(peticion);
    }

    private void guardarDevuelveLoMismo() {
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---------- Login ----------

    @Test
    void loginCorrectoDevuelveUnTokenQueSirveParaEscribir() throws Exception {
        existeAdmin("admin", "clave-de-prueba");
        guardarDevuelveLoMismo();

        String cuerpo = login("""
                {"usuario": "admin", "contrasena": "clave-de-prueba"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not(emptyString())))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andExpect(jsonPath("$.usuario").value("admin"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String token = cuerpo.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        crearMovimiento(token).andExpect(status().isCreated());
    }

    @Test
    void contrasenaIncorrectaOUsuarioInexistenteResponden401ConElMismoMensaje() throws Exception {
        existeAdmin("admin", "clave-de-prueba");
        when(usuarioRepository.findByUsuario("nadie")).thenReturn(Optional.empty());

        login("""
                {"usuario": "admin", "contrasena": "otra"}""")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos"))
                .andExpect(jsonPath("$.token").doesNotExist());
        login("""
                {"usuario": "nadie", "contrasena": "clave-de-prueba"}""")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void loginConCamposVaciosOSinCuerpoResponde400() throws Exception {
        login("""
                {"usuario": "", "contrasena": "x"}""").andExpect(status().isBadRequest());
        login("""
                {"usuario": "admin"}""").andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login")).andExpect(status().isBadRequest());
        verify(usuarioRepository, never()).findByUsuario(anyString());
    }

    // ---------- 401 / 403 ----------

    @Test
    void escribirSinTokenResponde401ConElFormatoDeError() throws Exception {
        crearMovimiento(null)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("No autorizado"))
                .andExpect(jsonPath("$.mensaje").value(containsString("Authorization")))
                .andExpect(jsonPath("$.path").value("/api/movimientos"))
                .andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(delete("/api/movimientos/1")).andExpect(status().isUnauthorized());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void tokenSinElRolAdminResponde403() throws Exception {
        String token = servicioToken.emitir("visitante", "USUARIO").token();

        crearMovimiento(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Prohibido"));
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void tokenVencidoResponde401() throws Exception {
        // Emitido hace 3 h con una duración de 2 h: venció hace 1 h
        String token = servicioToken.emitir("admin", "ADMIN", Instant.now().minus(Duration.ofHours(3))).token();

        crearMovimiento(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(containsString("venció")));
    }

    @Test
    void tokenFirmadoConOtroSecretoResponde401() throws Exception {
        var otraClave = new SecretKeySpec(
                "otro-secreto-que-no-es-el-del-servidor-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String token = new ServicioToken(new NimbusJwtEncoder(new ImmutableSecret<>(otraClave)), 120)
                .emitir("admin", "ADMIN").token();

        crearMovimiento(token).andExpect(status().isUnauthorized());
        crearMovimiento("esto-no-es-un-jwt").andExpect(status().isUnauthorized());
    }

    // ---------- GET públicos ----------

    @Test
    void losGetSiguenSiendoPublicos() throws Exception {
        when(movimientoRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/movimientos")).andExpect(status().isOk());
    }
}
