package com.pokedex.service;

import com.pokedex.entity.Evolucion;
import com.pokedex.entity.Pokemon;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.EvolucionRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.service.EvolucionService.EtapaEvolucion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Criterios de F7: árbol de la cadena evolutiva armado desde la BD (simulada). */
class EvolucionServiceTest {

    private final EvolucionRepository evolucionRepository = mock(EvolucionRepository.class);
    private final PokemonRepository pokemonRepository = mock(PokemonRepository.class);
    private final EvolucionService servicio = new EvolucionService(evolucionRepository, pokemonRepository);

    private static Evolucion etapa(int id, String nombre, Integer pre, String condicion) {
        Evolucion e = new Evolucion();
        e.setIdPokedex(id);
        e.setNombre(nombre);
        e.setIdCadena(67);
        e.setIdPreEvolucion(pre);
        e.setCondicion(condicion);
        return e;
    }

    private static Pokemon pokemon(int id, String nombreEs) {
        Pokemon p = new Pokemon();
        p.setIdPokedex(id);
        p.setNombre(nombreEs.toLowerCase());
        p.setNombreEs(nombreEs);
        p.setImagenUrl("https://img/" + id + ".png");
        return p;
    }

    /** Cadena de Eevee: 8 ramas, guardadas en desorden. Sylveon (700) no está importado. */
    private void cadenaDeEevee() {
        List<Evolucion> etapas = new ArrayList<>(List.of(
                etapa(700, "sylveon", 133, "Subiendo de nivel, con afecto alto"),
                etapa(134, "vaporeon", 133, "Usando Piedra Agua"),
                etapa(197, "umbreon", 133, "Subiendo de nivel, con amistad alta, de noche"),
                etapa(133, "eevee", null, null),
                etapa(135, "jolteon", 133, "Usando Piedra Trueno"),
                etapa(471, "glaceon", 133, "Usando Piedra Hielo"),
                etapa(136, "flareon", 133, "Usando Piedra Fuego"),
                etapa(470, "leafeon", 133, "Usando Piedra Hoja"),
                etapa(196, "espeon", 133, "Subiendo de nivel, con amistad alta, de día")));
        for (Evolucion e : etapas) {
            when(evolucionRepository.findById(e.getIdPokedex())).thenReturn(Optional.of(e));
        }
        when(evolucionRepository.findByIdCadena(67)).thenReturn(etapas);
        List<Pokemon> importados = new ArrayList<>();
        for (Evolucion e : etapas) {
            if (e.getIdPokedex() != 700) {
                importados.add(pokemon(e.getIdPokedex(), Character.toUpperCase(e.getNombre().charAt(0)) + e.getNombre().substring(1)));
            }
        }
        for (Pokemon p : importados) {
            when(pokemonRepository.findById(p.getIdPokedex())).thenReturn(Optional.of(p));
        }
        when(pokemonRepository.findById(700)).thenReturn(Optional.of(pokemon(700, "Sylveon")));
        when(pokemonRepository.findAllById(any())).thenReturn(importados);
    }

    @Test
    void eeveeTieneOchoRamasOrdenadasPorIdPokedex() {
        cadenaDeEevee();

        EtapaEvolucion base = servicio.obtenerCadena(133);

        assertThat(base.idPokedex()).isEqualTo(133);
        assertThat(base.condicion()).isNull();
        assertThat(base.evoluciones()).extracting(EtapaEvolucion::idPokedex)
                .containsExactly(134, 135, 136, 196, 197, 470, 471, 700);
        assertThat(base.evoluciones()).allSatisfy(rama -> assertThat(rama.evoluciones()).isEmpty());
        assertThat(base.evoluciones().get(0).condicion()).isEqualTo("Usando Piedra Agua");
    }

    @Test
    void desdeCualquierEtapaSeDevuelveLaCadenaCompletaDesdeLaBase() {
        cadenaDeEevee();

        assertThat(servicio.obtenerCadena(700)).isEqualTo(servicio.obtenerCadena(133));
    }

    @Test
    void unaEtapaNoImportadaTraeNombreEsEImagenEnNull() {
        cadenaDeEevee();

        EtapaEvolucion sylveon = servicio.obtenerCadena(133).evoluciones().get(7);

        assertThat(sylveon.nombre()).isEqualTo("sylveon");
        assertThat(sylveon.nombreEs()).isNull();
        assertThat(sylveon.imagenUrl()).isNull();
    }

    @Test
    void cadenaLinealDeTresEtapas() {
        List<Evolucion> etapas = List.of(etapa(1, "bulbasaur", null, null),
                etapa(2, "ivysaur", 1, "Nivel 16"), etapa(3, "venusaur", 2, "Nivel 32"));
        etapas.forEach(e -> when(evolucionRepository.findById(e.getIdPokedex())).thenReturn(Optional.of(e)));
        when(evolucionRepository.findByIdCadena(67)).thenReturn(etapas);
        when(pokemonRepository.findById(3)).thenReturn(Optional.of(pokemon(3, "Venusaur")));
        when(pokemonRepository.findAllById(any())).thenReturn(List.of());

        EtapaEvolucion base = servicio.obtenerCadena(3);

        assertThat(base.nombre()).isEqualTo("bulbasaur");
        assertThat(base.evoluciones().get(0).condicion()).isEqualTo("Nivel 16");
        assertThat(base.evoluciones().get(0).evoluciones().get(0).condicion()).isEqualTo("Nivel 32");
    }

    // T-08 · F7: pedir a Pikachu (25) devuelve una cadena que empieza en Pichu (172)
    @Test
    void alPedirAPikachuLaCadenaEmpiezaEnPichu() {
        List<Evolucion> etapas = List.of(etapa(172, "pichu", null, null),
                etapa(25, "pikachu", 172, "Subiendo de nivel, con amistad alta"),
                etapa(26, "raichu", 25, "Usando Piedra Trueno"));
        etapas.forEach(e -> when(evolucionRepository.findById(e.getIdPokedex())).thenReturn(Optional.of(e)));
        when(evolucionRepository.findByIdCadena(67)).thenReturn(etapas);
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(pokemon(25, "Pikachu")));
        when(pokemonRepository.findAllById(any())).thenReturn(List.of());

        EtapaEvolucion raiz = servicio.obtenerCadena(25);

        assertThat(raiz.idPokedex()).isEqualTo(172);
        assertThat(raiz.evoluciones()).extracting(EtapaEvolucion::idPokedex).containsExactly(25);
        assertThat(raiz.evoluciones().get(0).evoluciones()).extracting(EtapaEvolucion::idPokedex).containsExactly(26);
    }

    @Test
    void unPokemonSinCadenaGuardadaDevuelveUnaSolaEtapa() {
        when(pokemonRepository.findById(128)).thenReturn(Optional.of(pokemon(128, "Tauros")));
        when(evolucionRepository.findById(128)).thenReturn(Optional.empty());

        EtapaEvolucion tauros = servicio.obtenerCadena(128);

        assertThat(tauros.nombreEs()).isEqualTo("Tauros");
        assertThat(tauros.evoluciones()).isEmpty();
    }

    @Test
    void unPokemonInexistenteLanza404() {
        when(pokemonRepository.findById(9999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtenerCadena(9999)).isInstanceOf(ResourceNotFoundException.class);
    }
}
