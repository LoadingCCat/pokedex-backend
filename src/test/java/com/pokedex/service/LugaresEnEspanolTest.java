package com.pokedex.service;

import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Ruta;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-07 (interventoría 4.2) · criterios de F6: nombres de lugares en español.
 * «Bosque Verde», «Calle Victoria» y «Ruta 10» de Kanto, y ningún nombre con «(Kanto)» ni «/».
 * Los ids de Kanto (155, 152 y 89) son los de {@code datos/enlaces-wikidex.json} del frontend.
 */
class LugaresEnEspanolTest {

    private static final String API = "https://pokeapi.co/api/v2";

    private final PokemonRepository pokemonRepository = mock(PokemonRepository.class);
    private final RutaRepository rutaRepository = mock(RutaRepository.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final Map<String, Object> pokeapi = new HashMap<>();

    @BeforeEach
    void simularPokeapi() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenAnswer(i -> pokeapi.get((String) i.getArgument(0)));
        when(restTemplate.getForObject(anyString(), eq(List.class))).thenAnswer(i -> pokeapi.get((String) i.getArgument(0)));
        when(pokemonRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(pokemonRepository.save(any(Pokemon.class))).thenAnswer(i -> i.getArgument(0));
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(i -> i.getArgument(0));
        when(rutaRepository.findByIdPokeapi(anyInt())).thenReturn(Optional.empty());
    }

    private static Map<String, Object> stat(String nombre, int valor) {
        return Map.of("stat", Map.of("name", nombre), "base_stat", valor);
    }

    /** Importa el Pokémon 1, que aparece en un lugar (idLugar) cuya única traducción de la PokeAPI es la inglesa. */
    private String nombreImportadoDelLugar(int idLugar, String slugDelLugar, String nombreEnIngles) {
        pokeapi.put(API + "/pokemon/1", Map.of(
                "id", 1, "name", "uno", "height", 7, "weight", 69,
                "types", List.of(Map.of("type", Map.of("name", "grass"))),
                "stats", List.of(stat("hp", 45), stat("attack", 49), stat("defense", 49),
                        stat("special-attack", 65), stat("special-defense", 65), stat("speed", 45)),
                "moves", List.of(),
                "sprites", Map.of("front_default", "https://img/1.png")));
        Map<String, Object> especie = new HashMap<>();
        especie.put("names", List.of(Map.of("name", "Uno", "language", Map.of("name", "es"))));
        especie.put("generation", Map.of("name", "generation-i"));
        especie.put("flavor_text_entries", List.of());
        pokeapi.put(API + "/pokemon-species/1", especie);
        pokeapi.put(API + "/pokemon/1/encounters", List.of(Map.of(
                "location_area", Map.of("name", slugDelLugar + "-area", "url", API + "/location-area/1"),
                "version_details", List.of())));
        pokeapi.put(API + "/location-area/1", Map.of("location", Map.of("url", API + "/location/" + idLugar)));
        pokeapi.put(API + "/location/" + idLugar, Map.of("id", idLugar, "name", slugDelLugar,
                "names", List.of(Map.of("name", nombreEnIngles, "language", Map.of("name", "en"))),
                "region", Map.of("name", "kanto")));

        new PokemonService(pokemonRepository, mock(TipoRepository.class), mock(MovimientoRepository.class),
                rutaRepository, mock(EvolucionRepository.class), mock(RutaExcluidaRepository.class),
                mock(PokemonExcluidoRepository.class), restTemplate, new SyncTaskExecutor())
                .importarPokemonsMasivo(1);

        ArgumentCaptor<Pokemon> captor = ArgumentCaptor.forClass(Pokemon.class);
        verify(pokemonRepository).save(captor.capture());
        assertThat(captor.getValue().getRutas()).hasSize(1);
        return captor.getValue().getRutas().get(0).getNombre();
    }

    @Test
    void bosqueVerdeDeKanto() {
        assertThat(nombreImportadoDelLugar(155, "viridian-forest", "Viridian Forest")).isEqualTo("Bosque Verde");
    }

    @Test
    void calleVictoriaDeKantoSinElSufijoDeRegionNiLaBarra() {
        assertThat(nombreImportadoDelLugar(152, "victory-road", "Victory Road")).isEqualTo("Calle Victoria");
    }

    @Test
    void ruta10DeKantoSinElSufijoDeRegion() {
        assertThat(nombreImportadoDelLugar(89, "kanto-route-10", "Kanto Route 10")).isEqualTo("Ruta 10");
    }

    @Test
    void ningunNombreDelArchivoDeLugaresTerminaEnKantoNiLlevaBarra() throws IOException {
        Properties lugares = new Properties();
        try (InputStream entrada = getClass().getResourceAsStream("/lugares-es.properties")) {
            assertThat(entrada).isNotNull();
            lugares.load(new java.io.InputStreamReader(entrada, java.nio.charset.StandardCharsets.UTF_8));
        }

        assertThat(lugares).isNotEmpty();
        assertThat(lugares.values().stream().map(Object::toString))
                .noneMatch(nombre -> nombre.contains("(Kanto)"))
                .noneMatch(nombre -> nombre.contains("/"));
        assertThat(lugares.getProperty("155")).isEqualTo("Bosque Verde");
        assertThat(lugares.getProperty("152")).isEqualTo("Calle Victoria");
        assertThat(lugares.getProperty("89")).isEqualTo("Ruta 10");
    }
}
