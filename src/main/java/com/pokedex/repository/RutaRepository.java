package com.pokedex.repository;

import com.pokedex.entity.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface RutaRepository extends JpaRepository<Ruta, Integer> {

    // Se compara nombre + región porque "Ruta 1" existe en Kanto, Teselia, Alola, Galar...
    boolean existsByNombreIgnoreCaseAndRegionIgnoreCase(String nombre, String region);

    List<Ruta> findByRegionIgnoreCaseOrderByNombreAsc(String region);

    Optional<Ruta> findByIdPokeapi(Integer idPokeapi);

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
