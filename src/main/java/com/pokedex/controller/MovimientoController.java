package com.pokedex.controller;

import com.pokedex.entity.Movimiento;
import com.pokedex.exception.BadRequestException;
import com.pokedex.exception.CamposInvalidosException;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.service.DatosMovimiento;
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
    public ResponseEntity<Movimiento> guardarMovimiento(@RequestBody DatosMovimiento datos) {
        validar(datos);
        String nombre = datos.nombre().trim();
        if (movimientoRepository.existsByNombreIgnoreCase(nombre)) {
            throw CamposInvalidosException.de("nombre", "Ya existe un movimiento llamado '" + datos.nombre() + "'");
        }
        // Se copian solo los campos permitidos: el id y esCompetitivo los asigna el servidor
        Movimiento movimiento = new Movimiento();
        movimiento.setNombre(datos.nombre());
        movimiento.setNombreEs(datos.nombreEs());
        movimiento.setTipo(datos.tipo());
        movimiento.setCategoria(datos.categoria());
        movimiento.setPotencia(datos.potencia());
        prepararCampos(movimiento);
        return ResponseEntity.status(HttpStatus.CREATED).body(movimientoRepository.save(movimiento));
    }

    // PUT /api/movimientos/{id}
    @PutMapping("/{id}")
    public Movimiento actualizarMovimiento(@PathVariable Integer id, @RequestBody DatosMovimiento datos) {
        Movimiento existente = buscar(id);
        validar(datos);
        existente.setNombre(datos.nombre());
        existente.setCategoria(datos.categoria());
        existente.setPotencia(datos.potencia());
        // nombreEs y tipo son opcionales: si no vienen, se conserva el que ya tenía
        if (datos.nombreEs() != null) {
            existente.setNombreEs(datos.nombreEs());
        }
        if (datos.tipo() != null) {
            existente.setTipo(datos.tipo());
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

    private void validar(DatosMovimiento movimiento) {
        // CamposInvalidosException es un 400 con el mismo mensaje y, además, "campos" (F15)
        if (movimiento.nombre() == null || movimiento.nombre().isBlank()) {
            throw CamposInvalidosException.de("nombre", "El nombre del movimiento es obligatorio");
        }
        if (movimiento.nombre().trim().length() > DatosMovimiento.MAX_NOMBRE) {
            throw CamposInvalidosException.de("nombre",
                    "El nombre no puede pasar de " + DatosMovimiento.MAX_NOMBRE + " caracteres");
        }
        if (movimiento.categoria() == null || CATEGORIAS.stream()
                .noneMatch(c -> c.equalsIgnoreCase(movimiento.categoria().trim()))) {
            throw CamposInvalidosException.de("categoria", "La categoría debe ser una de: Físico, Especial, Estado");
        }
        if (movimiento.potencia() != null && movimiento.potencia() < 0) {
            throw CamposInvalidosException.de("potencia", "La potencia no puede ser negativa");
        }
        String tipo = movimiento.tipo();
        if (tipo != null && !tipo.isBlank() && !TIPOS.contains(tipo.trim().toLowerCase())) {
            throw CamposInvalidosException.de("tipo", "El tipo debe ser uno de: " + String.join(", ", TIPOS));
        }
        if (movimiento.nombreEs() != null && movimiento.nombreEs().trim().length() > DatosMovimiento.MAX_NOMBRE_ES) {
            throw CamposInvalidosException.de("nombreEs",
                    "El nombre en español no puede pasar de " + DatosMovimiento.MAX_NOMBRE_ES + " caracteres");
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
