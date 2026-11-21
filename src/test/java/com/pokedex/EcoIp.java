package com.pokedex;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo para pruebas: devuelve la IP que ve el servidor, la misma que usará el límite de F20
 * ({@code request.getRemoteAddr()}). Se importa con {@code @Import} en las pruebas que la necesitan.
 */
@RestController
class EcoIp {

    @GetMapping("/api/prueba/ip")
    String ip(HttpServletRequest peticion) {
        return peticion.getRemoteAddr();
    }
}
