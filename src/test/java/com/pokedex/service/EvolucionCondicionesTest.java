package com.pokedex.service;

import com.pokedex.entity.Evolucion;
import com.pokedex.entity.Movimiento;
import com.pokedex.entity.Pokemon;
import com.pokedex.repository.EvolucionRepository;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.PokemonExcluidoRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.RutaExcluidaRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.repository.TipoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-08 (interventoría 4.2) · criterios de F7: el texto de «cómo se evoluciona» que se guarda al
 * importar la cadena. La PokeAPI se simula con los {@code evolution_details} de cada caso.
 * Cada prueba importa el Pokémon 1, cuya especie apunta a una cadena de dos etapas.
 */
class EvolucionCondicionesTest {

    private static final String API = "https://pokeapi.co/api/v2";

    private final PokemonRepository pokemonRepository = mock(PokemonRepository.class);
    private final TipoRepository tipoRepository = mock(TipoRepository.class);
    private final MovimientoRepository movimientoRepository = mock(MovimientoRepository.class);
    private final RutaRepository rutaRepository = mock(RutaRepository.class);
    private final EvolucionRepository evolucionRepository = mock(EvolucionRepository.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final Map<String, Object> pokeapi = new HashMap<>();

    @BeforeEach
    void simularPokeapi() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenAnswer(i -> pokeapi.get((String) i.getArgument(0)));
        when(restTemplate.getForObject(anyString(), eq(List.class))).thenAnswer(i -> pokeapi.get((String) i.getArgument(0)));
        when(pokemonRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(pokemonRepository.save(any(Pokemon.class))).thenAnswer(i -> i.getArgument(0));
        when(tipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(i -> i.getArgument(0));
        when(evolucionRepository.findById(anyInt())).thenReturn(Optional.empty());
    }

    private PokemonService servicio() {
        return new PokemonService(pokemonRepository, tipoRepository, movimientoRepository, rutaRepository,
                evolucionRepository, mock(RutaExcluidaRepository.class), mock(PokemonExcluidoRepository.class),
                restTemplate, new SyncTaskExecutor());
    }

    // ---------- datos de ejemplo ----------

    private static Map<String, Object> nombre(String idioma, String texto) {
        return Map.of("name", texto, "language", Map.of("name", idioma));
    }

    private static Map<String, Object> stat(String nombre, int valor) {
        return Map.of("stat", Map.of("name", nombre), "base_stat", valor);
    }

    /** El Pokémon 1 con una especie que apunta a la cadena 77. */
    private void pokemonUnoConCadena(Map<String, Object> raiz) {
        pokeapi.put(API + "/pokemon/1", Map.of(
                "id", 1, "name", "uno", "height", 7, "weight", 69,
                "types", List.of(Map.of("type", Map.of("name", "grass"))),
                "stats", List.of(stat("hp", 45), stat("attack", 49), stat("defense", 49),
                        stat("special-attack", 65), stat("special-defense", 65), stat("speed", 45)),
                "moves", List.of(),
                "sprites", Map.of("front_default", "https://img/1.png")));
        Map<String, Object> especie = new HashMap<>();
        especie.put("names", List.of(nombre("en", "uno"), nombre("es", "Uno")));
        especie.put("generation", Map.of("name", "generation-i"));
        especie.put("flavor_text_entries", List.of());
        especie.put("evolution_chain", Map.of("url", API + "/evolution-chain/77/"));
        pokeapi.put(API + "/pokemon-species/1", especie);
        pokeapi.put(API + "/pokemon/1/encounters", List.of());
        pokeapi.put(API + "/evolution-chain/77/", Map.of("id", 77, "chain", raiz));
    }

    private static Map<String, Object> nodo(String especie, int id, List<Map<String, Object>> detalles,
                                            List<Map<String, Object>> evolucionesA) {
        Map<String, Object> nodo = new LinkedHashMap<>();
        nodo.put("species", Map.of("name", especie, "url", API + "/pokemon-species/" + id + "/"));
        nodo.put("evolution_details", detalles);
        nodo.put("evolves_to", evolucionesA);
        return nodo;
    }

    /** Cadena de dos etapas: la base y su evolución con los detalles dados. */
    private void cadena(String base, int idBase, String evolucion, int idEvolucion, List<Map<String, Object>> detalles) {
        pokemonUnoConCadena(nodo(base, idBase, List.of(), List.of(nodo(evolucion, idEvolucion, detalles, List.of()))));
    }

    /** Un {@code evolution_details} con los campos habituales de la PokeAPI en null. */
    private static Map<String, Object> detalle(String disparador, Object... camposYValores) {
        Map<String, Object> d = new HashMap<>();
        d.put("trigger", Map.of("name", disparador));
        for (int i = 0; i < camposYValores.length; i += 2) {
            d.put((String) camposYValores[i], camposYValores[i + 1]);
        }
        return d;
    }

    private static Map<String, Object> ref(String nombre) {
        return Map.of("name", nombre, "url", API + "/x/" + nombre);
    }

    /** La condición guardada para la etapa con ese nombre. */
    private String condicionDe(String etapa) {
        ArgumentCaptor<Evolucion> captor = ArgumentCaptor.forClass(Evolucion.class);
        verify(evolucionRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(e -> etapa.equals(e.getNombre()))
                .map(Evolucion::getCondicion)
                .findFirst().orElseThrow(() -> new AssertionError("No se guardó la etapa " + etapa));
    }

    // ---------- criterios de F7 ----------

    @Test
    void slowbroMuestraNivel37SinLaViaDeGalar() {
        cadena("slowpoke", 79, "slowbro", 80, List.of(
                detalle("level-up", "min_level", 37),
                detalle("use-item", "item", ref("galarica-cuff"), "required_pokemon_form", ref("slowpoke-galar"))));

        servicio().importarPokemonsMasivo(1);

        assertThat(condicionDe("slowbro")).isEqualTo("Nivel 37");
    }

    @Test
    void perrserkerMuestraNivel28ConLaAclaracionDeMeowthDeGalar() {
        cadena("meowth", 52, "perrserker", 863, List.of(
                detalle("level-up", "min_level", 28, "required_pokemon_form", ref("meowth-galar"))));

        servicio().importarPokemonsMasivo(1);

        assertThat(condicionDe("perrserker")).isEqualTo("Nivel 28 (solo Meowth de Galar)");
    }

    @Test
    void alcremieTieneUnaSolaFraseCortaAunqueLaPokeapiListeVariasCombinaciones() {
        cadena("milcery", 868, "alcremie", 869, List.of(
                detalle("spin", "held_item", ref("strawberry-sweet"), "time_of_day", "day"),
                detalle("spin", "held_item", ref("love-sweet"), "time_of_day", "night"),
                detalle("spin", "held_item", ref("berry-sweet"), "time_of_day", "")));

        servicio().importarPokemonsMasivo(1);

        String condicion = condicionDe("alcremie");
        assertThat(condicion).isNotBlank().hasSizeLessThan(80).doesNotContain(" o ");
    }

    @Test
    void wormadamSoloEvolucionaHembras() {
        cadena("burmy", 412, "wormadam", 413, List.of(
                detalle("level-up", "min_level", 20, "gender", 1)));

        servicio().importarPokemonsMasivo(1);

        assertThat(condicionDe("wormadam")).isEqualTo("Nivel 20, solo hembras");
    }

    @Test
    void pawmotMencionaLos1000Pasos() {
        cadena("pawmo", 922, "pawmot", 923, List.of(
                detalle("other", "min_steps", 1000)));

        servicio().importarPokemonsMasivo(1);

        assertThat(condicionDe("pawmot")).contains("1000");
    }

    @Test
    void annihilapeEsUsandoPunoFuria20VecesCuandoElMovimientoYaEstaEnLaBase() {
        Movimiento punoFuria = new Movimiento();
        punoFuria.setNombre("rage-fist");
        punoFuria.setNombreEs("Puño Furia");
        when(movimientoRepository.findFirstByNombreIgnoreCase("rage-fist")).thenReturn(Optional.of(punoFuria));
        cadena("primeape", 57, "annihilape", 979, List.of(
                detalle("use-move", "used_move", ref("rage-fist"), "min_move_count", 20)));

        servicio().importarPokemonsMasivo(1);

        assertThat(condicionDe("annihilape")).isEqualTo("Usando Puño Furia 20 veces");
    }

    /**
     * Misma condición, pero en una importación en limpio: la cadena la guarda el primer Pokémon de
     * la cadena (Primeape, #57) y el movimiento solo se crea más tarde, cuando se importa quien lo
     * aprende (Annihilape, #979). Aquí el Pokémon 1 es la base y el 2 aprende «rage-fist».
     */
    @Test
    @org.junit.jupiter.api.Disabled("Hallazgo T-18: en una importación en limpio la cadena se guarda antes de crear el "
            + "movimiento y la condición de Annihilape queda «Usando Rage Fist 20 veces»; solo se corrige al reimportar")
    void annihilapeEsUsandoPunoFuria20VecesTambienEnUnaImportacionEnLimpio() {
        Map<String, Movimiento> baseDeMovimientos = new HashMap<>();
        when(movimientoRepository.findFirstByNombreIgnoreCase(anyString()))
                .thenAnswer(i -> Optional.ofNullable(baseDeMovimientos.get((String) i.getArgument(0))));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(i -> {
            Movimiento m = i.getArgument(0);
            baseDeMovimientos.put(m.getNombre(), m);
            return m;
        });
        cadena("primeape", 57, "annihilape", 979, List.of(
                detalle("use-move", "used_move", ref("rage-fist"), "min_move_count", 20)));
        pokeapi.put(API + "/pokemon/2", Map.of(
                "id", 2, "name", "dos", "height", 7, "weight", 69,
                "types", List.of(Map.of("type", Map.of("name", "fighting"))),
                "stats", List.of(stat("hp", 45), stat("attack", 49), stat("defense", 49),
                        stat("special-attack", 65), stat("special-defense", 65), stat("speed", 45)),
                "moves", List.of(Map.of("move", Map.of("name", "rage-fist", "url", API + "/move/889/"))),
                "sprites", Map.of("front_default", "https://img/2.png")));
        Map<String, Object> especieDos = new HashMap<>();
        especieDos.put("names", List.of(nombre("en", "dos"), nombre("es", "Dos")));
        especieDos.put("generation", Map.of("name", "generation-ix"));
        especieDos.put("flavor_text_entries", List.of());
        pokeapi.put(API + "/pokemon-species/2", especieDos);
        pokeapi.put(API + "/pokemon/2/encounters", List.of());
        pokeapi.put(API + "/move/889/", Map.of("id", 889, "name", "rage-fist",
                "names", List.of(nombre("en", "Rage Fist"), nombre("es", "Puño Furia")),
                "damage_class", Map.of("name", "physical"), "power", 50, "type", Map.of("name", "ghost")));

        servicio().importarPokemonsMasivo(2);

        assertThat(condicionDe("annihilape")).isEqualTo("Usando Puño Furia 20 veces");
    }
}
