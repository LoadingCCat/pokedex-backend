package com.pokedex.service;

import com.pokedex.entity.Usuario;
import com.pokedex.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F15: el administrador inicial sale de ADMIN_USERNAME / ADMIN_PASSWORD, con BCrypt. */
class AdministradorInicialTest {

    private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
    private final BCryptPasswordEncoder cifrador = new BCryptPasswordEncoder();

    @Test
    void creaElAdministradorConLaContrasenaCifrada() {
        when(repositorio.existsByUsuario("admin")).thenReturn(false);

        assertThat(new AdministradorInicial(repositorio, cifrador, " admin ", "clave-de-prueba").crearSiFalta()).isTrue();

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).save(guardado.capture());
        assertThat(guardado.getValue().getUsuario()).isEqualTo("admin");
        assertThat(guardado.getValue().getRol()).isEqualTo("ADMIN");
        assertThat(guardado.getValue().getContrasena()).isNotEqualTo("clave-de-prueba").startsWith("$2");
        assertThat(cifrador.matches("clave-de-prueba", guardado.getValue().getContrasena())).isTrue();
    }

    @Test
    void siYaExisteNoLoTocaNiCambiaSuContrasena() {
        when(repositorio.existsByUsuario("admin")).thenReturn(true);

        assertThat(new AdministradorInicial(repositorio, cifrador, "admin", "otra").crearSiFalta()).isFalse();
        verify(repositorio, never()).save(any());
    }

    @Test
    void sinUsuarioOSinContrasenaNoCreaNada() {
        assertThat(new AdministradorInicial(repositorio, cifrador, "", "clave").crearSiFalta()).isFalse();
        assertThat(new AdministradorInicial(repositorio, cifrador, "admin", "").crearSiFalta()).isFalse();
        assertThat(new AdministradorInicial(repositorio, cifrador, null, null).crearSiFalta()).isFalse();
        verify(repositorio, never()).save(any());
    }
}
