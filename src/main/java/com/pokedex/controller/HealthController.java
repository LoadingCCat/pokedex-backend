package com.pokedex.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** F20: sirve para despertar el servidor en Render y para el monitoreo. No consulta la base. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Map<String, String> estado() {
        return Map.of("estado", "ok");
    }
}
