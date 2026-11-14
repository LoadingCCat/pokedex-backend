package com.pokedex.repository;

import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Ruta;
import com.pokedex.entity.Tipo;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtros combinados de GET /api/pokemons (F8). Cada filtro es opcional (null = no se aplica)
 * y todos se unen con Y.
 *
 * tipo y región se resuelven con subconsultas EXISTS en vez de JOIN: así un Pokémon no sale
 * repetido aunque tenga varias rutas en la región, el total de la paginación es correcto y
 * el orden (?sort=) funciona sin DISTINCT.
 */
public final class PokemonFiltros {

    private PokemonFiltros() {
    }

    /**
     * @param nombre     texto parcial; se busca en nombre (slug) y en nombreEs
     * @param generacion 1 a 9
     * @param tipo       nombre del tipo en inglés (fire, water...)
     * @param region     Pokémon nativos de la región o que aparecen en alguna de sus rutas
     * @param obtencion  valor exacto de tipoObtencion (ya normalizado por el controller)
     */
    public static Specification<Pokemon> con(String nombre, Integer generacion, String tipo,
                                             String region, String obtencion) {
        return (pokemon, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();

            if (nombre != null) {
                String patron = "%" + nombre.toLowerCase() + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(pokemon.get("nombre")), patron),
                        cb.like(cb.lower(pokemon.get("nombreEs")), patron)));
            }
            if (generacion != null) {
                condiciones.add(cb.equal(pokemon.get("generacion"), generacion));
            }
            if (tipo != null) {
                Subquery<Integer> conTipo = consulta.subquery(Integer.class);
                Root<Pokemon> otro = conTipo.from(Pokemon.class);
                Join<Pokemon, Tipo> tipos = otro.join("tipos");
                conTipo.select(cb.literal(1)).where(
                        cb.equal(otro.get("idPokedex"), pokemon.get("idPokedex")),
                        cb.equal(cb.lower(tipos.get("nombre")), tipo.toLowerCase()));
                condiciones.add(cb.exists(conTipo));
            }
            if (region != null) {
                Subquery<Integer> enRutaDeRegion = consulta.subquery(Integer.class);
                Root<Pokemon> otro = enRutaDeRegion.from(Pokemon.class);
                Join<Pokemon, Ruta> rutas = otro.join("rutas");
                enRutaDeRegion.select(cb.literal(1)).where(
                        cb.equal(otro.get("idPokedex"), pokemon.get("idPokedex")),
                        cb.equal(cb.lower(rutas.get("region")), region.toLowerCase()));
                condiciones.add(cb.or(
                        cb.equal(cb.lower(pokemon.get("region")), region.toLowerCase()),
                        cb.exists(enRutaDeRegion)));
            }
            if (obtencion != null) {
                condiciones.add(igualSinMayusculas(cb, pokemon, "tipoObtencion", obtencion));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
    }

    private static Predicate igualSinMayusculas(CriteriaBuilder cb, Root<Pokemon> pokemon,
                                                String campo, String valor) {
        return cb.equal(cb.lower(pokemon.get(campo)), valor.toLowerCase());
    }
}
