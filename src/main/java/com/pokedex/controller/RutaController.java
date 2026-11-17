package com.pokedex.controller;

import com.pokedex.entity.Ruta;
import com.pokedex.entity.RutaExcluida;
import com.pokedex.exception.CamposInvalidosException;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.RutaExcluidaRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.service.PokemonService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rutas")
public class RutaController {

    private final RutaRepository rutaRepository;
    private final RutaExcluidaRepository rutaExcluidaRepository;
    private final PokemonService pokemonService;

    public RutaController(RutaRepository rutaRepository, RutaExcluidaRepository rutaExcluidaRepository,
                          PokemonService pokemonService) {
        this.rutaRepository = rutaRepository;
        this.rutaExcluidaRepository = rutaExcluidaRepository;
        this.pokemonService = pokemonService;
    }

    // GET /api/rutas?page=0&size=20&sort=nombre  (por defecto, ordenadas por nombre)
    @GetMapping
    public Page<Ruta> listarRutas(@SortDefault("nombre") Pageable pageable) {
        return rutaRepository.findAll(pageable);
    }

    // GET /api/rutas/regiones  -> ["Kanto", "Johto", ...] (útil para llenar un <select>)
    @GetMapping("/regiones")
    public List<String> listarRegiones() {
        return pokemonService.listarRegiones();
    }

    // GET /api/rutas/region/kanto
    @GetMapping("/region/{region}")
    public Page<Ruta> listarPorRegion(@PathVariable String region, @SortDefault("nombre") Pageable pageable) {
        return rutaRepository.findByRegionIgnoreCase(region, pageable);
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
            throw CamposInvalidosException.de("nombre", "Ya existe la ruta '" + ruta.getNombre() + "' en " + ruta.getRegion());
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

    // DELETE /api/rutas/{id}  (F15, solo ADMIN) -> 204
    // Primero la quita de los Pokémon; si es un lugar importado, lo anota en ruta_excluida para que
    // la reimportación no lo vuelva a crear (no se pisan los cambios hechos a mano).
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> eliminarRuta(@PathVariable Integer id) {
        Ruta ruta = buscar(id);
        rutaRepository.desvincularDePokemons(id);
        if (ruta.getIdPokeapi() != null) {
            rutaExcluidaRepository.save(new RutaExcluida(ruta.getIdPokeapi()));
        }
        rutaRepository.delete(ruta);
        return ResponseEntity.noContent().build();
    }

    private Ruta buscar(Integer id) {
        return rutaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta", id));
    }

    private void validar(Ruta ruta) {
        // CamposInvalidosException es un 400 con el mismo mensaje y, además, "campos" (F15)
        if (ruta.getNombre() == null || ruta.getNombre().isBlank()) {
            throw CamposInvalidosException.de("nombre", "El nombre de la ruta es obligatorio");
        }
        if (ruta.getRegion() == null || ruta.getRegion().isBlank()) {
            throw CamposInvalidosException.de("region", "La región de la ruta es obligatoria");
        }
    }
}
