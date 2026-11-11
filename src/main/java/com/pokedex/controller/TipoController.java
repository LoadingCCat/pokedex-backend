package com.pokedex.controller;

import com.pokedex.entity.Tipo;
import com.pokedex.repository.TipoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tipos")
public class TipoController {

    private final TipoRepository tipoRepository;

    public TipoController(TipoRepository tipoRepository) {
        this.tipoRepository = tipoRepository;
    }

    // Listar todos los tipos de Pokémon disponibles en la BD
    @GetMapping
    public List<Tipo> listarTipos() {
        return tipoRepository.findAll();
    }
}