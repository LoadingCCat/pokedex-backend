package com.pokedex.repository;

import com.pokedex.entity.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface RutaRepository extends JpaRepository<Ruta, Integer> {

    // Se compara nombre + región porque "Ruta 1" existe en Kanto, Teselia, Alola, Galar...
    boolean existsByNombreIgnoreCaseAndRegionIgnoreCase(String nombre, String region);

    Page<Ruta> findByRegionIgnoreCase(String region, Pageable pageable);

    Optional<Ruta> findByIdPokeapi(Integer idPokeapi);

    // Rutas que ya tiene un Pokémon (la importación las conserva si /encounters falla)
    @Query("SELECT r FROM Pokemon p JOIN p.rutas r WHERE p.idPokedex = :idPokedex")
    List<Ruta> buscarPorPokemon(@Param("idPokedex") Integer idPokedex);

    // Quita la ruta de todos los Pokémon que aparecen en ella (necesario antes de borrarla, F15)
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM pokemon_ruta WHERE id_ruta = :idRuta", nativeQuery = true)
    int desvincularDePokemons(@Param("idRuta") Integer idRuta);

    // ---- Limpieza de las rutas de ejemplo que creaba la versión anterior del importador ----
    // Primero se quitan los vínculos con Pokémon y luego las rutas.
    // Las rutas creadas a mano con POST no se tocan (no tienen esa descripción).

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM pokemon_ruta WHERE id_ruta IN ("
            + "SELECT id_ruta FROM ruta WHERE id_pokeapi IS NULL AND descripcion LIKE 'Zona de exploración%')",
            nativeQuery = true)
    int desvincularRutasDeEjemplo();

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM ruta WHERE id_pokeapi IS NULL AND descripcion LIKE 'Zona de exploración%'",
            nativeQuery = true)
    int borrarRutasDeEjemplo();
}
