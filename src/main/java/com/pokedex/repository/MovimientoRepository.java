package com.pokedex.repository;

import com.pokedex.entity.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface MovimientoRepository extends JpaRepository<Movimiento, Integer> {

    // Consulta directa en la BD (antes se cargaba la tabla completa por cada movimiento)
    boolean existsByNombreIgnoreCase(String nombre);

    // findFirst: si por algún motivo hubiera dos con el mismo nombre, no falla
    Optional<Movimiento> findFirstByNombreIgnoreCase(String nombre);

    List<Movimiento> findByCategoriaIgnoreCase(String categoria);

    List<Movimiento> findByEsCompetitivoTrue();

    List<Movimiento> findByNombreContainingIgnoreCase(String nombre);

    // Movimientos que puede aprender un Pokémon, ordenados por nombre
    @Query("SELECT m FROM Pokemon p JOIN p.movimientos m WHERE p.idPokedex = :idPokedex ORDER BY m.nombre")
    List<Movimiento> buscarPorPokemon(@Param("idPokedex") Integer idPokedex);

    // Quita el movimiento de todos los Pokémon que lo aprenden (necesario antes de borrarlo)
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM pokemon_movimiento WHERE id_movimiento = :idMovimiento", nativeQuery = true)
    int desvincularDePokemons(@Param("idMovimiento") Integer idMovimiento);
}
