package com.pokedex.service;

import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Tipo;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.util.TiposEspanol;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * F20: los 3 Pokémon más parecidos al identificado, leídos de PostgreSQL. Primero los que
 * comparten algún tipo (menor diferencia de total, luego misma generación, luego idPokedex);
 * si no son 3, se completan con los de total más cercano de cualquier tipo.
 */
@Service
public class SimilaresService {

    static final int CANTIDAD = 3;

    /** Un similar con la razón (en español) por la que lo es. */
    public record Similar(Pokemon pokemon, String motivo) {}

    private final PokemonRepository pokemonRepository;

    public SimilaresService(PokemonRepository pokemonRepository) {
        this.pokemonRepository = pokemonRepository;
    }

    @Transactional(readOnly = true)
    public List<Similar> similaresDe(Integer idPokedex) {
        // Se lee aquí dentro, en la transacción, para poder recorrer sus tipos (lazy)
        Pokemon identificado = pokemonRepository.findById(idPokedex)
                .orElseThrow(() -> new ResourceNotFoundException("Pokémon", idPokedex));
        Integer total = identificado.getTotalEstadisticas();
        if (total == null) {
            throw new IllegalStateException("El Pokémon " + identificado.getIdPokedex() + " no tiene estadísticas completas");
        }
        List<Tipo> tiposIdentificado = identificado.getTipos() == null ? List.of() : identificado.getTipos();
        Set<Long> idsTipos = new HashSet<>();
        tiposIdentificado.forEach(t -> idsTipos.add(t.getId()));

        List<Similar> resultado = new ArrayList<>();
        Set<Integer> excluidos = new HashSet<>();
        excluidos.add(identificado.getIdPokedex());

        if (!idsTipos.isEmpty()) {
            for (Pokemon p : pokemonRepository.similaresConTipoCompartido(identificado.getIdPokedex(), idsTipos,
                    total, identificado.getGeneracion(), PageRequest.of(0, CANTIDAD))) {
                resultado.add(new Similar(p, motivo(p, tiposIdentificado, total)));
                excluidos.add(p.getIdPokedex());
            }
        }
        if (resultado.size() < CANTIDAD) {
            for (Pokemon p : pokemonRepository.similaresPorTotal(excluidos, total, identificado.getGeneracion(),
                    PageRequest.of(0, CANTIDAD - resultado.size()))) {
                resultado.add(new Similar(p, motivo(p, tiposIdentificado, total)));
            }
        }
        return resultado;
    }

    private static String motivo(Pokemon similar, List<Tipo> tiposIdentificado, int totalIdentificado) {
        Set<Long> idsIdentificado = new HashSet<>();
        tiposIdentificado.forEach(t -> idsIdentificado.add(t.getId()));
        List<String> compartidos = new ArrayList<>();
        for (Tipo t : similar.getTipos() == null ? List.<Tipo>of() : similar.getTipos()) {
            if (idsIdentificado.contains(t.getId())) {
                compartidos.add(TiposEspanol.de(t.getNombre()));
            }
        }
        int diferencia = Math.abs(similar.getTotalEstadisticas() - totalIdentificado);
        String sobreTotal = diferencia == 0 ? "tiene el mismo total" : "su total difiere en " + diferencia;

        if (compartidos.isEmpty()) {
            return "No comparte tipo, pero " + sobreTotal;
        }
        String tipos = compartidos.size() == 1
                ? "el tipo " + compartidos.get(0)
                : "los tipos " + String.join(" y ", compartidos);
        return "Comparte " + tipos + " y " + sobreTotal;
    }
}
