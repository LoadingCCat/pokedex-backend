package com.pokedex.controller;

import com.pokedex.entity.Pokemon;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.service.PokemonService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pokemons")
public class PokemonController {

    private final PokemonRepository pokemonRepository;
    private final PokemonService pokemonService;

    public PokemonController(PokemonRepository pokemonRepository, PokemonService pokemonService) {
        this.pokemonRepository = pokemonRepository;
        this.pokemonService = pokemonService;
    }

    @GetMapping
    public Page<Pokemon> listarPokemons(Pageable pageable) {
        return pokemonRepository.findAll(pageable);
    }

    @GetMapping("/buscar")
    public Page<Pokemon> buscarPorNombre(@RequestParam String nombre, Pageable pageable) {
        return pokemonRepository.findByNombreContainingIgnoreCase(nombre, pageable);
    }

    @GetMapping("/generacion/{generacion}")
    public Page<Pokemon> listarPorGeneracion(@PathVariable Integer generacion, Pageable pageable) {
        return pokemonRepository.findByGeneracion(generacion, pageable);
    }

    @GetMapping("/{id}")
    public Pokemon obtenerPorId(@PathVariable int id) {
        return pokemonRepository.findById(id).orElse(null);
    }

    @GetMapping("/tipo/{tipo}")
    public Page<Pokemon> listarPorTipo(@PathVariable String tipo, Pageable pageable) {
        return pokemonRepository.findByTiposNombre(tipo, pageable);
    }

    @GetMapping("/region/{region}")
    public Page<Pokemon> listarPorRegion(@PathVariable String region, Pageable pageable) {
        return pokemonRepository.buscarPorRegion(region, pageable);
    }

    // Endpoint para importar un rango personalizado (Ej: /api/pokemons/importar-rango?total=50)
    @GetMapping("/importar-rango")
    public String importarRango(@RequestParam int total) {
        try {
            pokemonService.importarPokemonsMasivo(total);
            pokemonService.importarMovimientosMasivo(50);
            pokemonService.importarRutasAutomaticas();
            return "¡Importación masiva de " + total + " Pokémon, movimientos y rutas exitosa!";
        } catch (Exception e) {
            return "Error al importar: " + e.getMessage();
        }
    }

    // Endpoint maestro de automatización rápida
    @GetMapping("/importar-todo")
    public String importarTodoAutomaticamente() {
        try {
            pokemonService.importarPokemonsMasivo(50);
            pokemonService.importarMovimientosMasivo(50);
            pokemonService.importarRutasAutomaticas();
            
            return "¡Automatización exitosa! Pokémons, movimientos y rutas importados correctamente.";
        } catch (Exception e) {
            return "Error en la automatización: " + e.getMessage();
        }
    }
}