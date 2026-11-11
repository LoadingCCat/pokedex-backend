package com.pokedex.controller;

import com.pokedex.entity.Ruta;
import com.pokedex.repository.RutaRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rutas")
public class RutaController {

    private final RutaRepository rutaRepository;

    public RutaController(RutaRepository rutaRepository) {
        this.rutaRepository = rutaRepository;
    }

    // Listar todas las rutas
    @GetMapping
    public List<Ruta> listarRutas() {
        return rutaRepository.findAll();
    }

    // Registrar una nueva ruta
    @PostMapping
    public Ruta guardarRuta(@RequestBody Ruta ruta) {
        return rutaRepository.save(ruta);
    }

    // Buscar ruta por su ID
    @GetMapping("/{id}")
    public Ruta obtenerPorId(@PathVariable Integer id) {
        return rutaRepository.findById(id).orElse(null);
    }
}