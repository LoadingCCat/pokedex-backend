package com.pokedex.service;

import com.pokedex.entity.Movimiento;
import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Ruta;
import com.pokedex.entity.Tipo;
import com.pokedex.exception.ConflictException;
import com.pokedex.repository.EvolucionRepository;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.PokemonExcluidoRepository;
import com.pokedex.repository.RutaExcluidaRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.repository.TipoRepository;
import com.pokedex.service.PokemonService.ResultadoImportacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Criterios de F3, F10 y la decisión B.18 en la importación. La PokeAPI se simula con un
 * RestTemplate falso que responde los Map de cada URL; los repositorios también son simulados.
 */
class PokemonServiceTest {

    private static final String API = "https://pokeapi.co/api/v2";

    private final PokemonRepository pokemonRepository = mock(PokemonRepository.class);
    private final TipoRepository tipoRepository = mock(TipoRepository.class);
    private final MovimientoRepository movimientoRepository = mock(MovimientoRepository.class);
    private final RutaRepository rutaRepository = mock(RutaRepository.class);
    private final EvolucionRepository evolucionRepository = mock(EvolucionRepository.class);
    private final RutaExcluidaRepository rutaExcluidaRepository = mock(RutaExcluidaRepository.class);
    private final PokemonExcluidoRepository pokemonExcluidoRepository = mock(PokemonExcluidoRepository.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);

    /** URL → respuesta simulada de la PokeAPI. Una RuntimeException se lanza en vez de responder. */
    private final Map<String, Object> pokeapi = new HashMap<>();

    @BeforeEach
    void simularPokeapi() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenAnswer(i -> responder(i.getArgument(0)));
        when(restTemplate.getForObject(anyString(), eq(List.class))).thenAnswer(i -> responder(i.getArgument(0)));
        when(pokemonRepository.findById(anyInt())).thenReturn(Optional.empty());
        when(pokemonRepository.save(any(Pokemon.class))).thenAnswer(i -> i.getArgument(0));
        when(tipoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Object responder(String url) {
        Object respuesta = pokeapi.get(url);
        if (respuesta instanceof RuntimeException error) {
            throw error;
        }
        return respuesta;
    }

    private PokemonService servicio(TaskExecutor ejecutor) {
        return new PokemonService(pokemonRepository, tipoRepository, movimientoRepository, rutaRepository,
                evolucionRepository, rutaExcluidaRepository, pokemonExcluidoRepository, restTemplate, ejecutor);
    }

    // ---------- datos de ejemplo de la PokeAPI ----------

    private static Map<String, Object> nombre(String idioma, String texto) {
        return Map.of("name", texto, "language", Map.of("name", idioma));
    }

    private static Map<String, Object> stat(String nombre, int valor) {
        return Map.of("stat", Map.of("name", nombre), "base_stat", valor);
    }

    private void pokemon(int id, String slug, String nombreEs, String tipo) {
        pokeapi.put(API + "/pokemon/" + id, Map.of(
                "id", id, "name", slug, "height", 7, "weight", 69,
                "types", List.of(Map.of("type", Map.of("name", tipo))),
                "stats", List.of(stat("hp", 45), stat("attack", 49), stat("defense", 49),
                        stat("special-attack", 65), stat("special-defense", 65), stat("speed", 45)),
                "moves", List.of(),
                "sprites", Map.of("front_default", "https://img/" + id + ".png")));
        Map<String, Object> especie = new HashMap<>();
        especie.put("names", List.of(nombre("en", slug), nombre("es", nombreEs)));
        especie.put("generation", Map.of("name", "generation-i"));
        especie.put("flavor_text_entries", List.of(Map.of("flavor_text", "Descripción.", "language", Map.of("name", "es"))));
        pokeapi.put(API + "/pokemon-species/" + id, especie);
        pokeapi.put(API + "/pokemon/" + id + "/encounters", List.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> especie(int id) {
        return (Map<String, Object>) pokeapi.get(API + "/pokemon-species/" + id);
    }

    private Pokemon guardado() {
        ArgumentCaptor<Pokemon> captor = ArgumentCaptor.forClass(Pokemon.class);
        verify(pokemonRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------- F3 · importación de un Pokémon ----------

    @Test
    void importaDatosEstadisticasYNombreEnEspanol() {
        pokemon(1, "bulbasaur", "Bulbasaur", "grass");

        ResultadoImportacion resultado = servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1);

        assertThat(resultado).isEqualTo(new ResultadoImportacion("pokemons", 1, 0, 0));
        Pokemon p = guardado();
        assertThat(p.getNombre()).isEqualTo("bulbasaur");
        assertThat(p.getNombreEs()).isEqualTo("Bulbasaur");
        assertThat(p.getGeneracion()).isEqualTo(1);
        assertThat(p.getRegion()).isEqualTo("Kanto");
        assertThat(p.getAltura()).isEqualTo(0.7);
        assertThat(p.getPeso()).isEqualTo(6.9);
        assertThat(p.getTotalEstadisticas()).isEqualTo(318);
        assertThat(p.getTipos()).extracting("nombre").containsExactly("grass");
        assertThat(p.getDescripcion()).isEqualTo("Descripción.");
    }

    @Test
    void losInicialesSeMarcanComoInicial() {
        pokemon(1, "bulbasaur", "Bulbasaur", "grass");

        servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1);

        assertThat(guardado().getTipoObtencion()).isEqualTo("Inicial");
    }

    @Test
    void unPokemonQueNoApareceSalvajeYTienePreevolucionEsEvolucion() {
        pokemon(1, "bulbasaur", "Bulbasaur", "grass");
        pokemon(2, "ivysaur", "Ivysaur", "grass");
        especie(2).put("evolves_from_species", Map.of("name", "bulbasaur"));

        servicio(new SyncTaskExecutor()).importarPokemonsMasivo(2);

        ArgumentCaptor<Pokemon> captor = ArgumentCaptor.forClass(Pokemon.class);
        verify(pokemonRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getTipoObtencion()).isEqualTo("Evolución");
    }

    @Test
    void unTimeoutCuentaComoFallidoYLaImportacionSigue() {
        pokeapi.put(API + "/pokemon/1", new ResourceAccessException("Read timed out"));
        pokemon(2, "ivysaur", "Ivysaur", "grass");

        ResultadoImportacion resultado = servicio(new SyncTaskExecutor()).importarPokemonsMasivo(2);

        assertThat(resultado.importados()).isEqualTo(1);
        assertThat(resultado.fallidos()).isEqualTo(1);
        assertThat(guardado().getIdPokedex()).isEqualTo(2);
    }

    @Test
    void siFallanLosEncuentrosSeConservanLasRutasQueTenia() {
        pokemon(1, "bulbasaur", "Bulbasaur", "grass");
        pokeapi.put(API + "/pokemon/1/encounters", new ResourceAccessException("Read timed out"));
        Ruta bosque = new Ruta();
        bosque.setNombre("Bosque Verde");
        bosque.setRegion("Kanto");
        when(rutaRepository.buscarPorPokemon(1)).thenReturn(List.of(bosque));

        servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1);

        assertThat(guardado().getRutas()).containsExactly(bosque);
    }

    // T-03 · F3/F10: importar N Pokémon termina con importados = N y fallidos = 0
    // (el total real de 1025 contra la PokeAPI se comprueba a mano; aquí, con la PokeAPI simulada)
    @Test
    void importar10TerminaConLos10ImportadosYSinFallidos() {
        for (int id = 1; id <= 10; id++) {
            pokemon(id, "pokemon-" + id, "Pokémon " + id, "normal");
        }

        ResultadoImportacion resultado = servicio(new SyncTaskExecutor()).importarPokemonsMasivo(10);

        assertThat(resultado.importados()).isEqualTo(10);
        assertThat(resultado.fallidos()).isZero();
        verify(pokemonRepository, org.mockito.Mockito.times(10)).save(any(Pokemon.class));
    }

    @Test
    void importar1025TerminaConLos1025ImportadosYSinFallidos() {
        for (int id = 1; id <= 1025; id++) {
            pokemon(id, "pokemon-" + id, "Pokémon " + id, "normal");
        }

        ResultadoImportacion resultado = servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1025);

        assertThat(resultado.importados()).isEqualTo(1025);
        assertThat(resultado.fallidos()).isZero();
    }

    // ---------- B.28 · nombres de lugares en español ----------

    /** Bulbasaur aparece en un área del lugar 155 (Viridian Forest), con los nombres que se pasen. */
    private void lugarViridian(List<Map<String, Object>> nombres) {
        pokemon(1, "bulbasaur", "Bulbasaur", "grass");
        pokeapi.put(API + "/pokemon/1/encounters", List.of(Map.of(
                "location_area", Map.of("name", "viridian-forest-area", "url", API + "/location-area/1"),
                "version_details", List.of())));
        pokeapi.put(API + "/location-area/1", Map.of("location", Map.of("url", API + "/location/155")));
        pokeapi.put(API + "/location/155", Map.of("id", 155, "name", "viridian-forest", "names", nombres,
                "region", Map.of("name", "kanto")));
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static Ruta ruta(String nombre) {
        Ruta ruta = new Ruta();
        ruta.setIdPokeapi(155);
        ruta.setNombre(nombre);
        ruta.setRegion("Kanto");
        return ruta;
    }

    @Test
    void unLugarNuevoSinEspanolEnLaPokeapiTomaElNombreDeWikidex() {
        lugarViridian(List.of(nombre("en", "Viridian Forest")));
        when(rutaRepository.findByIdPokeapi(155)).thenReturn(Optional.empty());

        servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1);

        assertThat(guardado().getRutas()).extracting(Ruta::getNombre).containsExactly("Bosque Verde");
    }

    @Test
    void alReimportarUnaRutaConElNombreEnInglesPasaAlEspanol() {
        lugarViridian(List.of(nombre("en", "Viridian Forest")));
        Ruta existente = ruta("Viridian Forest");
        when(rutaRepository.findByIdPokeapi(155)).thenReturn(Optional.of(existente));

        servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1);

        assertThat(existente.getNombre()).isEqualTo("Bosque Verde");
        verify(rutaRepository).save(existente);
    }

    @Test
    void alReimportarNoSeTocaUnaRutaRenombradaConPut() {
        lugarViridian(List.of(nombre("en", "Viridian Forest")));
        Ruta renombrada = ruta("Mi bosque favorito");
        when(rutaRepository.findByIdPokeapi(155)).thenReturn(Optional.of(renombrada));

        servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1);

        assertThat(renombrada.getNombre()).isEqualTo("Mi bosque favorito");
        verify(rutaRepository, never()).save(renombrada);
    }

    @Test
    void elNombreEnEspanolDeLaPokeapiTienePrioridadSobreWikidex() {
        lugarViridian(List.of(nombre("en", "Viridian Forest"), nombre("es", "Bosque de la PokeAPI")));
        when(rutaRepository.findByIdPokeapi(155)).thenReturn(Optional.empty());

        servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1);

        assertThat(guardado().getRutas()).extracting(Ruta::getNombre).containsExactly("Bosque de la PokeAPI");
    }

    // ---------- F15 · un lugar borrado a mano no se vuelve a crear ----------

    @Test
    void alReimportarNoSeVuelveACrearUnLugarBorradoAMano() {
        lugarViridian(List.of(nombre("en", "Viridian Forest")));
        when(rutaExcluidaRepository.existsById(155)).thenReturn(true);

        servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1);

        verify(rutaRepository, never()).save(any(Ruta.class));
        assertThat(guardado().getRutas()).isEmpty();
    }

    // ---------- F15 · la reimportación respeta el panel ----------

    @Test
    void alReimportarSeSaltaUnPokemonBorradoAMano() {
        pokemon(1, "bulbasaur", "Bulbasaur", "grass");
        when(pokemonExcluidoRepository.existsById(1)).thenReturn(true);

        ResultadoImportacion resultado = servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1);

        assertThat(resultado).isEqualTo(new ResultadoImportacion("pokemons", 0, 1, 0));
        verify(pokemonRepository, never()).save(any(Pokemon.class));
    }

    @Test
    void alReimportarUnPokemonEditadoAManoConservaSusDatosTiposYRutas() {
        pokemon(1, "bulbasaur", "Bulbasaur", "grass");
        Pokemon editado = new Pokemon();
        editado.setIdPokedex(1);
        editado.setNombre("bulbasaur");
        editado.setNombreEs("Mi Bulbasaur");
        editado.setAtaque(99);
        editado.setTipoObtencion("Regalo");
        editado.setTipos(List.of(new Tipo("fire")));
        editado.setRutas(List.of());
        editado.setEditadoAMano(true);
        when(pokemonRepository.findById(1)).thenReturn(Optional.of(editado));

        ResultadoImportacion resultado = servicio(new SyncTaskExecutor()).importarPokemonsMasivo(1);

        assertThat(resultado).isEqualTo(new ResultadoImportacion("pokemons", 1, 0, 0));
        Pokemon p = guardado();
        assertThat(p.getNombreEs()).isEqualTo("Mi Bulbasaur");
        assertThat(p.getAtaque()).isEqualTo(99);
        assertThat(p.getTipoObtencion()).isEqualTo("Regalo");
        assertThat(p.getTipos()).extracting("nombre").containsExactly("fire");
        assertThat(p.getEditadoAMano()).isTrue();
    }

    // ---------- B.18 · reimportar movimientos existentes ----------

    @Test
    void alReimportarUnMovimientoSoloSeActualizaSuNombreEnEspanol() {
        Movimiento editadoConPut = new Movimiento();
        editadoConPut.setNombre("pound");
        editadoConPut.setCategoria("Especial");
        editadoConPut.setPotencia(50);
        when(movimientoRepository.findFirstByNombreIgnoreCase("pound")).thenReturn(Optional.of(editadoConPut));
        pokeapi.put(API + "/move/1", Map.of("name", "pound", "power", 40, "type", Map.of("name", "normal"),
                "damage_class", Map.of("name", "physical"), "names", List.of(nombre("es", "Destructor"))));

        ResultadoImportacion resultado = servicio(new SyncTaskExecutor()).importarMovimientosMasivo(1);

        assertThat(resultado).isEqualTo(new ResultadoImportacion("movimientos", 0, 1, 0));
        assertThat(editadoConPut.getNombreEs()).isEqualTo("Destructor");
        assertThat(editadoConPut.getCategoria()).isEqualTo("Especial");
        assertThat(editadoConPut.getPotencia()).isEqualTo(50);
        // No tenía tipo: se llena al reimportar
        assertThat(editadoConPut.getTipo()).isEqualTo("normal");
    }

    @Test
    void alReimportarNoSePisaUnTipoQueYaTenia() {
        Movimiento conTipo = new Movimiento();
        conTipo.setNombre("pound");
        conTipo.setNombreEs("Destructor");
        conTipo.setCategoria("Físico");
        conTipo.setPotencia(40);
        conTipo.setTipo("fighting");
        when(movimientoRepository.findFirstByNombreIgnoreCase("pound")).thenReturn(Optional.of(conTipo));
        pokeapi.put(API + "/move/1", Map.of("name", "pound", "power", 40, "type", Map.of("name", "normal"),
                "damage_class", Map.of("name", "physical"), "names", List.of(nombre("es", "Destructor"))));

        servicio(new SyncTaskExecutor()).importarMovimientosMasivo(1);

        assertThat(conTipo.getTipo()).isEqualTo("fighting");
        // Nada cambió: no se guarda
        verify(movimientoRepository, never()).save(any(Movimiento.class));
    }

    @Test
    void unMovimientoNuevoSeCreaConCategoriaTraducidaYEsCompetitivo() {
        when(movimientoRepository.findFirstByNombreIgnoreCase("hyper-beam")).thenReturn(Optional.empty());
        pokeapi.put(API + "/move/1", Map.of("name", "hyper-beam", "power", 150, "type", Map.of("name", "normal"),
                "damage_class", Map.of("name", "special"), "names", List.of(nombre("es", "Hiperrayo"))));

        servicio(new SyncTaskExecutor()).importarMovimientosMasivo(1);

        ArgumentCaptor<Movimiento> captor = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(captor.capture());
        assertThat(captor.getValue().getCategoria()).isEqualTo("Especial");
        assertThat(captor.getValue().getNombreEs()).isEqualTo("Hiperrayo");
        assertThat(captor.getValue().getEsCompetitivo()).isTrue();
        assertThat(captor.getValue().getTipo()).isEqualTo("normal");
    }

    // ---------- F10 · importación en segundo plano ----------

    @Test
    void laImportacionTerminaConLosCincoResultados() {
        pokemon(1, "bulbasaur", "Bulbasaur", "grass");
        PokemonService servicio = servicio(new SyncTaskExecutor());

        servicio.iniciarImportacion(1, 0);

        ProgresoImportacion.Estado estado = servicio.progresoImportacion();
        assertThat(estado.estado()).isEqualTo("terminada");
        assertThat(estado.procesados()).isEqualTo(1);
        assertThat(estado.fin()).isNotNull();
        assertThat(estado.resultados()).extracting(ResultadoImportacion::recurso)
                .containsExactly("pokemons", "rutas", "movimientos-aprendidos", "cadenas-evolutivas", "movimientos");
    }

    @Test
    void unaSegundaImportacionMientrasCorreLaPrimeraLanzaConflictoYAlTerminarSeAceptaOtra() {
        List<Runnable> enEspera = new ArrayList<>();
        PokemonService servicio = servicio(enEspera::add); // el ejecutor guarda la tarea sin correrla

        ProgresoImportacion.Estado inicial = servicio.iniciarImportacion(1, 0);
        assertThat(inicial.estado()).isEqualTo("en-curso");
        assertThat(inicial.total()).isEqualTo(1);

        assertThatThrownBy(() -> servicio.iniciarImportacion(1, 0))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ya hay una importación en curso. Espera a que termine.");

        enEspera.get(0).run(); // la primera termina (la PokeAPI no responde nada: 1 fallido)
        assertThat(servicio.progresoImportacion().estado()).isEqualTo("terminada");
        assertThat(servicio.progresoImportacion().fallidos()).isEqualTo(1);

        servicio.iniciarImportacion(1, 0);
        assertThat(enEspera).hasSize(2);
    }

    @Test
    void unErrorInesperadoDejaElEstadoEnErrorYLiberaElBloqueo() {
        when(rutaRepository.desvincularRutasDeEjemplo()).thenThrow(new IllegalStateException("BD caída"));
        PokemonService servicio = servicio(new SyncTaskExecutor());

        servicio.iniciarImportacion(1, 0);

        ProgresoImportacion.Estado estado = servicio.progresoImportacion();
        assertThat(estado.estado()).isEqualTo("error");
        assertThat(estado.mensaje()).doesNotContain("BD caída");

        servicio.iniciarImportacion(1, 0); // no lanza 409
        verify(pokemonRepository, never()).save(any());
    }

    @Test
    void siElEjecutorRechazaLaTareaSeLiberaElBloqueo() {
        PokemonService servicio = servicio(tarea -> {
            throw new org.springframework.core.task.TaskRejectedException("sin hilos");
        });

        assertThatThrownBy(() -> servicio.iniciarImportacion(1, 0))
                .isInstanceOf(org.springframework.core.task.TaskRejectedException.class);
        assertThatThrownBy(() -> servicio.iniciarImportacion(1, 0))
                .isNotInstanceOf(ConflictException.class);
    }
}
