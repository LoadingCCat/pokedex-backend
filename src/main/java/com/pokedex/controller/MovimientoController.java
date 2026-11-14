package com.pokedex.controller;

import com.pokedex.entity.Movimiento;
import com.pokedex.exception.BadRequestException;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.MovimientoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    private static final Set<String> CATEGORIAS = Set.of("Físico", "Especial", "Estado");

    private final MovimientoRepository movimientoRepository;

    public MovimientoController(MovimientoRepository movimientoRepository) {
        this.movimientoRepository = movimientoRepository;
    }

    // GET /api/movimientos
    @GetMapping
    public List<Movimiento> listarMovimientos() {
        return movimientoRepository.findAll();
    }

    // GET /api/movimientos/{id}
    @GetMapping("/{id}")
    public Movimiento obtenerPorId(@PathVariable Integer id) {
        return buscar(id);
    }

    // GET /api/movimientos/buscar?nombre=thunder
    @GetMapping("/buscar")
    public List<Movimiento> buscarPorNombre(@RequestParam String nombre) {
        return movimientoRepository.findByNombreContainingIgnoreCase(nombre.trim());
    }

    // GET /api/movimientos/categoria/Especial
    @GetMapping("/categoria/{categoria}")
    public List<Movimiento> listarPorCategoria(@PathVariable String categoria) {
        return movimientoRepository.findByCategoriaIgnoreCase(categoria);
    }

    // GET /api/movimientos/competitivos  -> potencia > 80
    @GetMapping("/competitivos")
    public List<Movimiento> listarCompetitivos() {
        return movimientoRepository.findByEsCompetitivoTrue();
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
    }

    /** Normaliza el texto y calcula esCompetitivo (true si la potencia es mayor a 80). */
    private void prepararCampos(Movimiento movimiento) {
        movimiento.setNombre(movimiento.getNombre().trim());
        String categoria = movimiento.getCategoria().trim();
        movimiento.setCategoria(CATEGORIAS.stream()
                .filter(c -> c.equalsIgnoreCase(categoria)).findFirst().orElse(categoria));
        if (movimiento.getPotencia() == null) {
            movimiento.setPotencia(0);
        }
        movimiento.setEsCompetitivo(movimiento.getPotencia() > 80);
    }
}
