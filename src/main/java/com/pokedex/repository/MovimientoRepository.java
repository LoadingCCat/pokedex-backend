package com.pokedex.repository;

import com.pokedex.entity.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoRepository extends JpaRepository<Movimiento, Integer> {
}