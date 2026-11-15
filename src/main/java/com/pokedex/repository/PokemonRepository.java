package com.pokedex.repository;

import com.pokedex.entity.Pokemon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// JpaSpecificationExecutor: permite los filtros combinados de GET /pokemons (ver PokemonFiltros)
public interface PokemonRepository extends JpaRepository<Pokemon, Integer>, JpaSpecificationExecutor<Pokemon> {

    // Busca en el nombre original (slug en inglés) y en el nombre en español,
    // sin importar mayúsculas ni tildes (unaccent de PostgreSQL, F9)
    @Query(
        value = "SELECT p FROM Pokemon p "
              + "WHERE LOWER(function('unaccent', p.nombre)) LIKE LOWER(function('unaccent', CONCAT('%', :nombre, '%'))) "
              + "OR LOWER(function('unaccent', p.nombreEs)) LIKE LOWER(function('unaccent', CONCAT('%', :nombre, '%')))",
        countQuery = "SELECT COUNT(p) FROM Pokemon p "
              + "WHERE LOWER(function('unaccent', p.nombre)) LIKE LOWER(function('unaccent', CONCAT('%', :nombre, '%'))) "
              + "OR LOWER(function('unaccent', p.nombreEs)) LIKE LOWER(function('unaccent', CONCAT('%', :nombre, '%')))"
    )
    Page<Pokemon> buscarPorNombre(@Param("nombre") String nombre, Pageable pageable);

    Page<Pokemon> findByGeneracion(Integer generacion, Pageable pageable);

    // Ej. /obtencion/legendario, /obtencion/inicial
    Page<Pokemon> findByTipoObtencionIgnoreCase(String tipoObtencion, Pageable pageable);

    // IgnoreCase: /tipo/Fire y /tipo/fire devuelven lo mismo
    Page<Pokemon> findByTiposNombreIgnoreCase(String nombre, Pageable pageable);

    // Pokémon nativos de la región + Pokémon que se pueden encontrar en alguna ruta de esa región.
    // DISTINCT evita repetidos si un Pokémon aparece en varias rutas de la misma región.
    // countQuery es necesario para que la paginación cuente bien con DISTINCT + JOIN.
    @Query(
        value = "SELECT DISTINCT p FROM Pokemon p LEFT JOIN p.rutas r "
              + "WHERE LOWER(p.region) = LOWER(:region) OR LOWER(r.region) = LOWER(:region)",
        countQuery = "SELECT COUNT(DISTINCT p) FROM Pokemon p LEFT JOIN p.rutas r "
              + "WHERE LOWER(p.region) = LOWER(:region) OR LOWER(r.region) = LOWER(:region)"
    )
    Page<Pokemon> buscarPorRegion(@Param("region") String region, Pageable pageable);

    @Query(
        value = "SELECT DISTINCT p FROM Pokemon p JOIN p.rutas r WHERE r.idRuta = :idRuta",
        countQuery = "SELECT COUNT(DISTINCT p) FROM Pokemon p JOIN p.rutas r WHERE r.idRuta = :idRuta"
    )
    Page<Pokemon> buscarPorRuta(@Param("idRuta") Integer idRuta, Pageable pageable);
}
