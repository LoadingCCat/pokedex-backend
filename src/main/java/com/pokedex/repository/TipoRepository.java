package com.pokedex.repository;

import com.pokedex.entity.Tipo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoRepository extends JpaRepository<Tipo, Long> {
    
    // Método automático para buscar un tipo por su nombre (ej: "fire", "water")
    Tipo findByNombre(String nombre);
}