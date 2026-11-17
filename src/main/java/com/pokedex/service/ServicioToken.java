package com.pokedex.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Emite los JWT del login (F15): HS256, con el usuario en "sub" y el rol en "rol". */
@Service
public class ServicioToken {

    public record TokenEmitido(String token, Instant expiraEn) {
    }

    private final JwtEncoder codificador;
    private final Duration duracion;

    public ServicioToken(JwtEncoder codificador,
                         @Value("${pokedex.seguridad.token-minutos:120}") long minutos) {
        this.codificador = codificador;
        this.duracion = Duration.ofMinutes(minutos);
    }

    public TokenEmitido emitir(String usuario, String rol) {
        return emitir(usuario, rol, Instant.now());
    }

    /** Con la hora de emisión explícita (las pruebas la usan para crear un token ya vencido). */
    public TokenEmitido emitir(String usuario, String rol, Instant emitidoEn) {
        Instant expiraEn = emitidoEn.plus(duracion);
        JwtClaimsSet datos = JwtClaimsSet.builder()
                .issuer("pokedex-backend")
                .issuedAt(emitidoEn)
                .expiresAt(expiraEn)
                .subject(usuario)
                .claim("rol", rol)
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = codificador.encode(JwtEncoderParameters.from(cabecera, datos)).getTokenValue();
        return new TokenEmitido(token, expiraEn);
    }
}
