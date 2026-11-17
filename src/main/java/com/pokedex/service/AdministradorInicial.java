package com.pokedex.service;

import com.pokedex.entity.Usuario;
import com.pokedex.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Crea el administrador inicial al arrancar (F15) desde ADMIN_USERNAME y ADMIN_PASSWORD
 * (application-local.properties o variables de entorno). Solo lo crea si no existe: no
 * cambia la contraseña de un usuario que ya está. La contraseña nunca se escribe en el log.
 */
@Component
public class AdministradorInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministradorInicial.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder cifrador;
    private final String usuario;
    private final String contrasena;

    public AdministradorInicial(UsuarioRepository usuarioRepository, PasswordEncoder cifrador,
                                @Value("${pokedex.seguridad.admin-usuario:}") String usuario,
                                @Value("${pokedex.seguridad.admin-contrasena:}") String contrasena) {
        this.usuarioRepository = usuarioRepository;
        this.cifrador = cifrador;
        this.usuario = usuario == null ? "" : usuario.trim();
        this.contrasena = contrasena == null ? "" : contrasena;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        crearSiFalta();
    }

    /** @return true si lo creó */
    public boolean crearSiFalta() {
        if (usuario.isEmpty() || contrasena.isBlank()) {
            log.warn("Sin ADMIN_USERNAME o ADMIN_PASSWORD: no se crea el administrador inicial "
                    + "(el panel no tendrá con quién iniciar sesión si no hay usuarios)");
            return false;
        }
        if (usuarioRepository.existsByUsuario(usuario)) {
            return false;
        }
        usuarioRepository.save(new Usuario(usuario, cifrador.encode(contrasena), Usuario.ROL_ADMIN));
        log.info("Administrador inicial '{}' creado", usuario);
        return true;
    }
}
