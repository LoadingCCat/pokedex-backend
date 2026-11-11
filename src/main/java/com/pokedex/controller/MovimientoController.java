package com.pokedex.controller;

import com.pokedex.entity.Movimiento;
import com.pokedex.repository.MovimientoRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    private final MovimientoRepository movimientoRepository;

    public MovimientoController(MovimientoRepository movimientoRepository) {
        this.movimientoRepository = movimientoRepository;
    }

    // Listar todos los movimientos
    @GetMapping
    public List<Movimiento> listarMovimientos() {
        return movimientoRepository.findAll();
    }

    // Registrar un nuevo movimiento
    @PostMapping
    public Movimiento guardarMovimiento(@RequestBody Movimiento movimiento) {
        return movimientoRepository.save(movimiento);
    }

    // Buscar movimiento por su ID
    @GetMapping("/{id}")
    public Movimiento obtenerPorId(@PathVariable Integer id) {
        return movimientoRepository.findById(id).orElse(null);
    }
}