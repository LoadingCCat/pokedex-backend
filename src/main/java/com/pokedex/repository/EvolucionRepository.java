package com.pokedex.repository;

import com.pokedex.entity.Evolucion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvolucionRepository extends JpaRepository<Evolucion, Integer> {

    // Todas las etapas de una cadena evolutiva
    List<Evolucion> findByIdCadena(Integer idCadena);
}
