package com.pokedex.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * N-01: la IP con la que se cuentan los límites de uso (login e identificar).
 *
 * <p>El navegador no llama al backend: lo hace el servidor del frontend (Route Handlers de Next en
 * Vercel), así que la IP de la conexión es la de Vercel y todos los usuarios compartirían cupo. El
 * frontend manda la IP real en {@code X-Cliente-IP} y, para que nadie más pueda declarar la que
 * quiera, la acompaña de {@code X-Proxy-Secreto} con el valor de {@code PROXY_SECRETO}.
 *
 * <p>Se usa {@code X-Cliente-IP} solo si el secreto está configurado, la cabecera del secreto
 * coincide (comparación en tiempo constante) y el valor es una IP. En cualquier otro caso, y siempre
 * que {@code PROXY_SECRETO} no esté definido, la IP es la de {@link HttpServletRequest#getRemoteAddr()},
 * como antes.
 */
@Component
public class IpCliente {

    public static final String CABECERA_IP = "X-Cliente-IP";
    public static final String CABECERA_SECRETO = "X-Proxy-Secreto";

    /** Largo mínimo de PROXY_SECRETO si se define: uno corto se adivina. */
    static final int MINIMO_CARACTERES_SECRETO = 16;

    /** Solo caracteres de una IPv4 o IPv6 (sin zona), hasta 45: evita claves raras en el mapa de cupos. */
    private static final Pattern FORMA_DE_IP = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    /** SHA-256 del secreto (se comparan huellas, de largo fijo), o null si no hay secreto. */
    private final byte[] huellaDelSecreto;

    @Autowired
    public IpCliente(@Value("${pokedex.seguridad.proxy-secreto:}") String secreto) {
        if (secreto == null || secreto.isBlank()) {
            this.huellaDelSecreto = null;
            return;
        }
        if (secreto.length() < MINIMO_CARACTERES_SECRETO) {
            throw new IllegalStateException("PROXY_SECRETO es demasiado corto: usa al menos "
                    + MINIMO_CARACTERES_SECRETO + " caracteres al azar, o no lo definas");
        }
        this.huellaDelSecreto = huella(secreto);
    }

    /** La IP del cliente para los límites de uso. */
    public String de(HttpServletRequest request) {
        String conexion = request.getRemoteAddr();
        if (huellaDelSecreto == null) {
            return conexion;
        }
        String secreto = request.getHeader(CABECERA_SECRETO);
        if (secreto == null || !MessageDigest.isEqual(huellaDelSecreto, huella(secreto))) {
            return conexion;
        }
        String declarada = request.getHeader(CABECERA_IP);
        if (declarada == null || !FORMA_DE_IP.matcher(declarada.trim()).matches()) {
            return conexion;
        }
        return declarada.trim();
    }

    private static byte[] huella(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
