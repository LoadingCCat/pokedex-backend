package com.pokedex.repository;

import com.pokedex.entity.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Integer> {

    // Consulta directa en la BD (antes se cargaba la tabla completa por cada movimiento)
    boolean existsByNombreIgnoreCase(String nombre);

    List<Movimiento> findByCategoriaIgnoreCase(String categoria);

    List<Movimiento> findByEsCompetitivoTrue();

    List<Movimiento> findByNombreContainingIgnoreCase(String nombre);
}
