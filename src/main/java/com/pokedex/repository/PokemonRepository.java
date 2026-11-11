package com.pokedex.repository;

import com.pokedex.entity.Pokemon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PokemonRepository extends JpaRepository<Pokemon, Integer> {

    Page<Pokemon> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<Pokemon> findByGeneracion(Integer generacion, Pageable pageable);

    Page<Pokemon> findByTiposNombre(String nombre, Pageable pageable);

    @Query("SELECT p FROM Pokemon p JOIN p.rutas r WHERE LOWER(r.region) = LOWER(:region)")
    Page<Pokemon> buscarPorRegion(@Param("region") String region, Pageable pageable);
}