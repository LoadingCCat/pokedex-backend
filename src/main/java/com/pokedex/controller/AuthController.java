package com.pokedex.controller;

import com.pokedex.entity.Usuario;
import com.pokedex.exception.BadRequestException;
import com.pokedex.exception.NoAutorizadoException;
import com.pokedex.repository.UsuarioRepository;
import com.pokedex.service.ServicioToken;
import com.pokedex.service.ServicioToken.TokenEmitido;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Login del panel de administración (F15). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Mismo mensaje si falla el usuario o la contraseña: no se revela cuál de los dos. */
    static final String CREDENCIALES_INCORRECTAS = "Usuario o contraseña incorrectos";

    public record SolicitudLogin(String usuario, String contrasena) {
    }

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder cifrador;
    private final ServicioToken servicioToken;
    /** Hash para comparar cuando el usuario no existe, para que tarde lo mismo que uno real. */
    private final String hashDeRelleno;

    public AuthController(UsuarioRepository usuarioRepository, PasswordEncoder cifrador, ServicioToken servicioToken) {
        this.usuarioRepository = usuarioRepository;
        this.cifrador = cifrador;
        this.servicioToken = servicioToken;
        this.hashDeRelleno = cifrador.encode("relleno-para-igualar-tiempos");
    }

    // POST /api/auth/login  { "usuario": "...", "contrasena": "..." }
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody(required = false) SolicitudLogin solicitud) {
        if (solicitud == null || solicitud.usuario() == null || solicitud.usuario().isBlank()
                || solicitud.contrasena() == null || solicitud.contrasena().isEmpty()) {
            throw new BadRequestException("El usuario y la contraseña son obligatorios");
        }
        Optional<Usuario> encontrado = usuarioRepository.findByUsuario(solicitud.usuario().trim());
        String hash = encontrado.map(Usuario::getContrasena).orElse(hashDeRelleno);
        if (!cifrador.matches(solicitud.contrasena(), hash) || encontrado.isEmpty()) {
            throw new NoAutorizadoException(CREDENCIALES_INCORRECTAS);
        }

        Usuario usuario = encontrado.get();
        TokenEmitido emitido = servicioToken.emitir(usuario.getUsuario(), usuario.getRol());
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("token", emitido.token());
        respuesta.put("expiraEn", emitido.expiraEn().toString());
        respuesta.put("usuario", usuario.getUsuario());
        respuesta.put("rol", usuario.getRol());
        return respuesta;
    }
}
