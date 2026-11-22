package com.pokedex;

import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.TipoRepository;
import com.pokedex.service.PokemonService;
import com.pokedex.service.PokemonService.ResultadoImportacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-03 (interventoría 4.2) · criterios de F3 que necesitan la base real: tras importar 10,
 * {@code totalElements >= 10}, y repetir la misma importación no duplica. La PokeAPI va simulada.
 * (Importar los 1025 de verdad con 0 fallidos solo se comprueba a mano contra la PokeAPI real.)
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImportacionConPostgresTest {

    private static final String API = "https://pokeapi.co/api/v2";

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry propiedades) {
        PostgresEmbebido.registrar(propiedades);
        propiedades.add("pokedex.seguridad.jwt-secreto", () -> ConSeguridad.SECRETO_DE_PRUEBA);
        propiedades.add("pokedex.seguridad.admin-usuario", () -> "admin-prueba");
        propiedades.add("pokedex.seguridad.admin-contrasena", () -> "clave-de-prueba");
    }

    @MockitoBean
    private RestTemplate restTemplate;

    @Autowired
    private PokemonService servicio;
    @Autowired
    private PokemonRepository pokemonRepository;
    @Autowired
    private TipoRepository tipoRepository;
    @Autowired
    private MockMvc mvc;

    @BeforeEach
    @AfterEach
    void limpiar() {
        pokemonRepository.deleteAll();
        tipoRepository.deleteAll();
    }

    private static Map<String, Object> stat(String nombre, int valor) {
        return Map.of("stat", Map.of("name", nombre), "base_stat", valor);
    }

    /** Simula los tres recursos de la PokeAPI de los Pokémon 1..total. */
    private void simularPokeapi(int total) {
        Map<String, Object> respuestas = new HashMap<>();
        for (int id = 1; id <= total; id++) {
            respuestas.put(API + "/pokemon/" + id, Map.of(
                    "id", id, "name", "pokemon-" + id, "height", 7, "weight", 69,
                    "types", List.of(Map.of("type", Map.of("name", id % 2 == 0 ? "fire" : "grass"))),
                    "stats", List.of(stat("hp", 45), stat("attack", 49), stat("defense", 49),
                            stat("special-attack", 65), stat("special-defense", 65), stat("speed", 45)),
                    "moves", List.of(),
                    "sprites", Map.of("front_default", "https://img/" + id + ".png")));
            Map<String, Object> especie = new HashMap<>();
            especie.put("names", List.of(Map.of("name", "Pokémon " + id, "language", Map.of("name", "es"))));
            especie.put("generation", Map.of("name", "generation-i"));
            especie.put("flavor_text_entries", List.of());
            respuestas.put(API + "/pokemon-species/" + id, especie);
            respuestas.put(API + "/pokemon/" + id + "/encounters", List.of());
        }
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenAnswer(i -> respuestas.get((String) i.getArgument(0)));
        when(restTemplate.getForObject(anyString(), eq(List.class))).thenAnswer(i -> respuestas.get((String) i.getArgument(0)));
    }

    @Test
    void despuesDeImportar10HayAlMenos10YRepetirNoDuplica() throws Exception {
        simularPokeapi(10);

        ResultadoImportacion primera = servicio.importarPokemonsMasivo(10);

        assertThat(primera.importados()).isEqualTo(10);
        assertThat(primera.fallidos()).isZero();
        mvc.perform(get("/api/pokemons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(10)));
        long tiposTrasLaPrimera = tipoRepository.count();

        servicio.importarPokemonsMasivo(10);

        mvc.perform(get("/api/pokemons")).andExpect(jsonPath("$.page.totalElements").value(10));
        assertThat(pokemonRepository.count()).isEqualTo(10);
        assertThat(tipoRepository.count()).isEqualTo(tiposTrasLaPrimera);
    }
}
