package com.pokedex.controller;

import com.pokedex.entity.Ruta;
import com.pokedex.exception.BadRequestException;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.RutaRepository;
import com.pokedex.service.PokemonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rutas")
public class RutaController {

    private final RutaRepository rutaRepository;
    private final PokemonService pokemonService;

    public RutaController(RutaRepository rutaRepository, PokemonService pokemonService) {
        this.rutaRepository = rutaRepository;
        this.pokemonService = pokemonService;
    }

    // GET /api/rutas
    @GetMapping
    public List<Ruta> listarRutas() {
        return rutaRepository.findAll();
    }

    // GET /api/rutas/regiones  -> ["Kanto", "Johto", ...] (útil para llenar un <select>)
    @GetMapping("/regiones")
    public List<String> listarRegiones() {
        return pokemonService.listarRegiones();
    }

    // GET /api/rutas/region/kanto
    @GetMapping("/region/{region}")
    public List<Ruta> listarPorRegion(@PathVariable String region) {
        return rutaRepository.findByRegionIgnoreCaseOrderByNombreAsc(region);
    }

    // GET /api/rutas/{id}
    @GetMapping("/{id}")
    public Ruta obtenerPorId(@PathVariable Integer id) {
        return buscar(id);
    }

    // POST /api/rutas
    @PostMapping
    public ResponseEntity<Ruta> guardarRuta(@RequestBody Ruta ruta) {
        validar(ruta);
        if (rutaRepository.existsByNombreIgnoreCaseAndRegionIgnoreCase(ruta.getNombre().trim(), ruta.getRegion().trim())) {
            throw new BadRequestException("Ya existe la ruta '" + ruta.getNombre() + "' en " + ruta.getRegion());
        }
        ruta.setIdRuta(null); // evita sobrescribir una ruta existente enviando un id
        ruta.setIdPokeapi(null); // solo el importador asigna idPokeapi (marca las rutas reales de la PokeAPI)
        ruta.setNombre(ruta.getNombre().trim());
        ruta.setRegion(ruta.getRegion().trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(rutaRepository.save(ruta));
    }

    // PUT /api/rutas/{id}
    @PutMapping("/{id}")
    public Ruta actualizarRuta(@PathVariable Integer id, @RequestBody Ruta datos) {
        Ruta existente = buscar(id);
        validar(datos);
        existente.setNombre(datos.getNombre().trim());
        existente.setRegion(datos.getRegion().trim());
        existente.setDescripcion(datos.getDescripcion());
        return rutaRepository.save(existente);
    }

    private Ruta buscar(Integer id) {
        return rutaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta", id));
    }

    private void validar(Ruta ruta) {
        if (ruta.getNombre() == null || ruta.getNombre().isBlank()) {
            throw new BadRequestException("El nombre de la ruta es obligatorio");
        }
        if (ruta.getRegion() == null || ruta.getRegion().isBlank()) {
            throw new BadRequestException("La región de la ruta es obligatoria");
        }
    }
}
