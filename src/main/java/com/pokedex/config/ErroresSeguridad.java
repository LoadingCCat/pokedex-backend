package com.pokedex.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 401 y 403 de Spring Security con el mismo JSON que GlobalExceptionHandler
 * ({ timestamp, status, error, mensaje, path }). Los filtros de seguridad corren antes de los
 * controllers, así que esos errores no llegan al @RestControllerAdvice.
 */
@Component
public class ErroresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    // 401: sin token, o con uno inválido o vencido
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        String mensaje = ex instanceof InvalidBearerTokenException
                ? "El token no es válido o ya venció: vuelve a iniciar sesión"
                : "Hace falta iniciar sesión: envía el token en la cabecera Authorization: Bearer <token>";
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escribir(response, request, HttpStatus.UNAUTHORIZED, "No autorizado", mensaje);
    }

    // 403: token válido, pero sin el rol ADMIN
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        escribir(response, request, HttpStatus.FORBIDDEN, "Prohibido",
                "Tu usuario no tiene permiso para esta operación");
    }

    private static void escribir(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
                                 String error, String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now()
                + "\",\"status\":" + status.value()
                + ",\"error\":" + texto(error)
                + ",\"mensaje\":" + texto(mensaje)
                + ",\"path\":" + texto(request.getRequestURI()) + "}");
    }

    /** Cadena JSON con las comillas, barras y caracteres de control escapados. */
    static String texto(String valor) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
