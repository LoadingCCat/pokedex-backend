package com.pokedex.controller;

import com.pokedex.entity.Movimiento;
import com.pokedex.exception.BadRequestException;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.MovimientoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    private static final Set<String> CATEGORIAS = Set.of("Físico", "Especial", "Estado");
    // Los 18 tipos, con el nombre en inglés de la PokeAPI (como Tipo.nombre)
    private static final List<String> TIPOS = List.of("normal", "fighting", "flying", "poison", "ground",
            "rock", "bug", "ghost", "steel", "fire", "water", "grass", "electric", "psychic", "ice", "dragon",
            "dark", "fairy");

    private final MovimientoRepository movimientoRepository;

    public MovimientoController(MovimientoRepository movimientoRepository) {
        this.movimientoRepository = movimientoRepository;
    }

    // GET /api/movimientos?page=0&size=20&sort=nombre  (por defecto, ordenados por nombre)
    @GetMapping
    public Page<Movimiento> listarMovimientos(@SortDefault("nombre") Pageable pageable) {
        return movimientoRepository.findAll(pageable);
    }

    // GET /api/movimientos/{id}
    @GetMapping("/{id}")
    public Movimiento obtenerPorId(@PathVariable Integer id) {
        return buscar(id);
    }

    // GET /api/movimientos/buscar?nombre=thunder
    @GetMapping("/buscar")
    public Page<Movimiento> buscarPorNombre(@RequestParam String nombre,
                                            @SortDefault("nombre") Pageable pageable) {
        if (nombre.isBlank()) {
            throw new BadRequestException("El parámetro 'nombre' no puede estar vacío");
        }
        return movimientoRepository.buscarPorNombre(nombre.trim(), pageable);
    }

    // GET /api/movimientos/categoria/Especial
    @GetMapping("/categoria/{categoria}")
    public Page<Movimiento> listarPorCategoria(@PathVariable String categoria,
                                               @SortDefault("nombre") Pageable pageable) {
        return movimientoRepository.findByCategoriaIgnoreCase(categoria, pageable);
    }

    // GET /api/movimientos/competitivos  -> potencia > 80
    @GetMapping("/competitivos")
    public Page<Movimiento> listarCompetitivos(@SortDefault("nombre") Pageable pageable) {
        return movimientoRepository.findByEsCompetitivoTrue(pageable);
    }

    // POST /api/movimientos
    @PostMapping
    public ResponseEntity<Movimiento> guardarMovimiento(@RequestBody Movimiento movimiento) {
        validar(movimiento);
        if (movimientoRepository.existsByNombreIgnoreCase(movimiento.getNombre().trim())) {
            throw new BadRequestException("Ya existe un movimiento llamado '" + movimiento.getNombre() + "'");
        }
        movimiento.setIdMovimiento(null); // evita sobrescribir uno existente enviando un id
        prepararCampos(movimiento);
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoRepository.save(movimiento));
    }

    // PUT /api/movimientos/{id}
    @PutMapping("/{id}")
    public Movimiento actualizarMovimiento(@PathVariable Integer id, @RequestBody Movimiento datos) {
        Movimiento existente = buscar(id);
        validar(datos);
        existente.setNombre(datos.getNombre());
        existente.setCategoria(datos.getCategoria());
        existente.setPotencia(datos.getPotencia());
        // nombreEs y tipo son opcionales: si no vienen, se conserva el que ya tenía
        if (datos.getNombreEs() != null) {
            existente.setNombreEs(datos.getNombreEs());
        }
        if (datos.getTipo() != null) {
            existente.setTipo(datos.getTipo());
        }
        prepararCampos(existente);
        return movimientoRepository.save(existente);
    }

    // DELETE /api/movimientos/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarMovimiento(@PathVariable Integer id) {
        Movimiento movimiento = buscar(id);
        // Primero se quita de los Pokémon que lo aprenden; si no, la BD rechaza el borrado
        movimientoRepository.desvincularDePokemons(id);
        movimientoRepository.delete(movimiento);
        return ResponseEntity.noContent().build();
    }

    private Movimiento buscar(Integer id) {
        return movimientoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento", id));
    }

    private void validar(Movimiento movimiento) {
        if (movimiento.getNombre() == null || movimiento.getNombre().isBlank()) {
            throw new BadRequestException("El nombre del movimiento es obligatorio");
        }
        if (movimiento.getCategoria() == null || CATEGORIAS.stream()
                .noneMatch(c -> c.equalsIgnoreCase(movimiento.getCategoria().trim()))) {
            throw new BadRequestException("La categoría debe ser una de: Físico, Especial, Estado");
        }
        if (movimiento.getPotencia() != null && movimiento.getPotencia() < 0) {
            throw new BadRequestException("La potencia no puede ser negativa");
        }
        String tipo = movimiento.getTipo();
        if (tipo != null && !tipo.isBlank() && !TIPOS.contains(tipo.trim().toLowerCase())) {
            throw new BadRequestException("El tipo debe ser uno de: " + String.join(", ", TIPOS));
        }
    }

    /** Normaliza el texto y calcula esCompetitivo (true si la potencia es mayor a 80). */
    private void prepararCampos(Movimiento movimiento) {
        movimiento.setNombre(movimiento.getNombre().trim());
        if (movimiento.getNombreEs() != null) {
            String nombreEs = movimiento.getNombreEs().trim();
            movimiento.setNombreEs(nombreEs.isEmpty() ? null : nombreEs);
        }
        if (movimiento.getTipo() != null) {
            String tipo = movimiento.getTipo().trim().toLowerCase();
            movimiento.setTipo(tipo.isEmpty() ? null : tipo);
        }
        String categoria = movimiento.getCategoria().trim();
        movimiento.setCategoria(CATEGORIAS.stream()
                .filter(c -> c.equalsIgnoreCase(categoria)).findFirst().orElse(categoria));
        if (movimiento.getPotencia() == null) {
            movimiento.setPotencia(0);
        }
        movimiento.setEsCompetitivo(movimiento.getPotencia() > 80);
    }
}
