package com.pokedex.repository;

import com.pokedex.entity.Pokemon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

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

    // F20: el slug que devuelve la IA, sin importar mayúsculas ni tildes. List y no Optional,
    // porque nombre no es único en la BD; se toma el de menor idPokedex.
    @Query("SELECT p FROM Pokemon p "
         + "WHERE LOWER(function('unaccent', p.nombre)) = LOWER(function('unaccent', :slug)) "
         + "ORDER BY p.idPokedex")
    List<Pokemon> buscarPorSlug(@Param("slug") String slug, Pageable pageable);

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

    // F20: similares que comparten al menos un tipo (EXISTS, sin JOIN: no duplica a quien comparte dos).
    // totalEstadisticas no es columna: se calcula aquí. Orden: menor diferencia de total, misma
    // generación primero, idPokedex. Los que tienen alguna estadística null no cuentan.
    @Query("SELECT p FROM Pokemon p "
         + "WHERE p.idPokedex <> :idExcluido "
         + "AND p.ps IS NOT NULL AND p.ataque IS NOT NULL AND p.defensa IS NOT NULL "
         + "AND p.ataqueEspecial IS NOT NULL AND p.defensaEspecial IS NOT NULL AND p.velocidad IS NOT NULL "
         + "AND EXISTS (SELECT 1 FROM p.tipos t WHERE t.id IN :idsTipos) "
         + "ORDER BY ABS((p.ps + p.ataque + p.defensa + p.ataqueEspecial + p.defensaEspecial + p.velocidad) - :total), "
         + "CASE WHEN p.generacion = :generacion THEN 0 ELSE 1 END, p.idPokedex")
    List<Pokemon> similaresConTipoCompartido(@Param("idExcluido") Integer idExcluido,
                                             @Param("idsTipos") Collection<Long> idsTipos,
                                             @Param("total") int total,
                                             @Param("generacion") Integer generacion,
                                             Pageable pageable);

    // F20: relleno cuando no alcanzan los de tipo compartido: el total más cercano, sin importar el tipo
    @Query("SELECT p FROM Pokemon p "
         + "WHERE p.idPokedex NOT IN :idsExcluidos "
         + "AND p.ps IS NOT NULL AND p.ataque IS NOT NULL AND p.defensa IS NOT NULL "
         + "AND p.ataqueEspecial IS NOT NULL AND p.defensaEspecial IS NOT NULL AND p.velocidad IS NOT NULL "
         + "ORDER BY ABS((p.ps + p.ataque + p.defensa + p.ataqueEspecial + p.defensaEspecial + p.velocidad) - :total), "
         + "CASE WHEN p.generacion = :generacion THEN 0 ELSE 1 END, p.idPokedex")
    List<Pokemon> similaresPorTotal(@Param("idsExcluidos") Collection<Integer> idsExcluidos,
                                    @Param("total") int total,
                                    @Param("generacion") Integer generacion,
                                    Pageable pageable);

    @Query(
        value = "SELECT DISTINCT p FROM Pokemon p JOIN p.rutas r WHERE r.idRuta = :idRuta",
        countQuery = "SELECT COUNT(DISTINCT p) FROM Pokemon p JOIN p.rutas r WHERE r.idRuta = :idRuta"
    )
    Page<Pokemon> buscarPorRuta(@Param("idRuta") Integer idRuta, Pageable pageable);
}
