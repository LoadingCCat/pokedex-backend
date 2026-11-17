package com.pokedex.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pokedex.entity.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Seguridad de la API (F15), sin sesión: cada petición de escritura trae un JWT en
 * "Authorization: Bearer <token>", firmado con HS256 y el secreto JWT_SECRETO.
 *
 * - Todos los GET son públicos (el frontend de consulta no cambia), salvo las importaciones.
 * - POST /api/auth/login es público.
 * - POST, PUT, DELETE y la importación exigen el rol ADMIN: 401 sin token o con uno
 *   inválido o vencido, 403 si el token no tiene el rol (ErroresSeguridad, con el formato de
 *   error de siempre).
 * - CORS: solo los orígenes de pokedex.seguridad.cors-origenes (por defecto el front local).
 */
@Configuration
public class SeguridadConfig {

    /** Largo mínimo del secreto: HS256 necesita una clave de al menos 256 bits. */
    static final int MINIMO_BYTES_SECRETO = 32;

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ErroresSeguridad errores,
                                                 JwtAuthenticationConverter conversorRol) throws Exception {
        http
                // Sin cookies ni sesión: el token va en una cabecera, así que CSRF no aplica
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> { })
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // La importación es una escritura aunque acepte GET (para probarla desde el navegador)
                        .requestMatchers("/api/pokemons/importar-rango", "/api/pokemons/importar-todo")
                                .hasRole(Usuario.ROL_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().hasRole(Usuario.ROL_ADMIN))
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorRol))
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errores)
                        .accessDeniedHandler(errores));
        return http.build();
    }

    /** El claim "rol" del token (ADMIN) pasa a ser la autoridad ROLE_ADMIN. */
    @Bean
    public JwtAuthenticationConverter conversorRol() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("rol");
        autoridades.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(autoridades);
        return conversor;
    }

    /**
     * Clave HS256 a partir de JWT_SECRETO. Sin secreto, o con uno corto, el servidor no
     * arranca: no hay un valor por defecto en el código (no se suben credenciales).
     */
    @Bean
    public SecretKey claveJwt(@Value("${pokedex.seguridad.jwt-secreto:}") String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMO_BYTES_SECRETO) {
            throw new IllegalStateException("Falta el secreto del token: define JWT_SECRETO (al menos "
                    + MINIMO_BYTES_SECRETO + " caracteres) en application-local.properties o como variable de entorno");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder codificadorJwt(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    @Bean
    public JwtDecoder decodificadorJwt(SecretKey claveJwt) {
        // Comprueba la firma y la fecha de vencimiento (exp)
        return NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public PasswordEncoder cifradorContrasenas() {
        return new BCryptPasswordEncoder();
    }

    /** Reemplaza el CORS abierto ("*") de antes: el front local, con la cabecera Authorization. */
    @Bean
    public CorsConfigurationSource configuracionCors(
            @Value("${pokedex.seguridad.cors-origenes:http://localhost:3000}") String origenes) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", cors);
        return fuente;
    }
}
