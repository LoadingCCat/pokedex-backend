package com.pokedex.controller;

import com.pokedex.service.IdentificacionService;
import com.pokedex.service.IdentificacionService.Identificacion;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** F20: público (sin token), con límite de 10 por minuto por IP. Todo el orden vive en el servicio. */
@RestController
@RequestMapping("/api/pokemons")
public class IdentificacionController {

    private final IdentificacionService identificacionService;

    public IdentificacionController(IdentificacionService identificacionService) {
        this.identificacionService = identificacionService;
    }

    // Sin `consumes` y con required = false: una petición sin multipart o sin el campo llega como
    // foto null y ValidadorFoto responde 400 (después del límite por IP), no un 415 ni un 500
    @PostMapping("/identificar")
    public Identificacion identificar(@RequestPart(value = "foto", required = false) MultipartFile foto,
                                      HttpServletRequest request) {
        return identificacionService.identificar(request.getRemoteAddr(), foto);
    }
}
