package com.pokedex.service;

import com.pokedex.entity.Evolucion;
import com.pokedex.entity.Pokemon;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.EvolucionRepository;
import com.pokedex.repository.PokemonRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Arma la cadena evolutiva de un Pokémon a partir de lo guardado en la BD durante la
 * importación (no consulta la PokeAPI).
 */
@Service
public class EvolucionService {

    /**
     * Una etapa de la cadena con las etapas a las que evoluciona. Las ramas (ej. Eevee) son
     * varios elementos en "evoluciones". nombreEs e imagenUrl son null si ese Pokémon no
     * está importado; condicion es null en la etapa base.
     */
    public record EtapaEvolucion(Integer idPokedex, String nombre, String nombreEs, String imagenUrl,
                                 String condicion, List<EtapaEvolucion> evoluciones) {}

    private final EvolucionRepository evolucionRepository;
    private final PokemonRepository pokemonRepository;

    public EvolucionService(EvolucionRepository evolucionRepository, PokemonRepository pokemonRepository) {
        this.evolucionRepository = evolucionRepository;
        this.pokemonRepository = pokemonRepository;
    }

    /** Devuelve la cadena completa (desde su etapa base) a la que pertenece el Pokémon. */
    public EtapaEvolucion obtenerCadena(Integer idPokedex) {
        Pokemon pokemon = pokemonRepository.findById(idPokedex)
                .orElseThrow(() -> new ResourceNotFoundException("Pokémon", idPokedex));

        Evolucion propia = evolucionRepository.findById(idPokedex).orElse(null);
        if (propia == null) {
            // Importado antes de que existieran las cadenas, o su cadena falló al importar:
            // se devuelve él solo para que la ficha no se rompa (se completa al reimportar)
            return new EtapaEvolucion(pokemon.getIdPokedex(), pokemon.getNombre(), pokemon.getNombreEs(),
                    pokemon.getImagenUrl(), null, List.of());
        }

        List<Evolucion> etapas = evolucionRepository.findByIdCadena(propia.getIdCadena());
        Map<Integer, Evolucion> porId = new HashMap<>();
        Map<Integer, List<Evolucion>> hijosPorPadre = new HashMap<>();
        for (Evolucion etapa : etapas) {
            porId.put(etapa.getIdPokedex(), etapa);
            if (etapa.getIdPreEvolucion() != null) {
                hijosPorPadre.computeIfAbsent(etapa.getIdPreEvolucion(), k -> new ArrayList<>()).add(etapa);
            }
        }

        Map<Integer, Pokemon> importados = new HashMap<>();
        for (Pokemon p : pokemonRepository.findAllById(porId.keySet())) {
            importados.put(p.getIdPokedex(), p);
        }

        // Se sube desde el Pokémon pedido hasta la etapa base (la que no tiene preevolución guardada)
        Evolucion base = propia;
        Set<Integer> visitados = new HashSet<>();
        while (base.getIdPreEvolucion() != null && porId.containsKey(base.getIdPreEvolucion())
                && visitados.add(base.getIdPokedex())) {
            base = porId.get(base.getIdPreEvolucion());
        }
        return armar(base, hijosPorPadre, importados, new HashSet<>());
    }

    private EtapaEvolucion armar(Evolucion etapa, Map<Integer, List<Evolucion>> hijosPorPadre,
                                 Map<Integer, Pokemon> importados, Set<Integer> visitados) {
        visitados.add(etapa.getIdPokedex());
        List<EtapaEvolucion> evoluciones = new ArrayList<>();
        List<Evolucion> hijos = new ArrayList<>(hijosPorPadre.getOrDefault(etapa.getIdPokedex(), List.of()));
        hijos.sort(Comparator.comparing(Evolucion::getIdPokedex));
        for (Evolucion hijo : hijos) {
            if (!visitados.contains(hijo.getIdPokedex())) { // por si hubiera un ciclo en los datos
                evoluciones.add(armar(hijo, hijosPorPadre, importados, visitados));
            }
        }
        Pokemon pokemon = importados.get(etapa.getIdPokedex());
        return new EtapaEvolucion(
                etapa.getIdPokedex(),
                etapa.getNombre(),
                pokemon != null ? pokemon.getNombreEs() : null,
                pokemon != null ? pokemon.getImagenUrl() : null,
                etapa.getCondicion(),
                evoluciones);
    }
}
