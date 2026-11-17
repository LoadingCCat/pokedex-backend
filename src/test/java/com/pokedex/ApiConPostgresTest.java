package com.pokedex;

import com.pokedex.entity.Movimiento;
import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Ruta;
import com.pokedex.entity.Tipo;
import com.pokedex.repository.EvolucionRepository;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.repository.TipoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios que dependen de las consultas SQL reales (F2, F6, F8, F9 y el sort inválido),
 * probados de punta a punta (HTTP → controller → repositorio → PostgreSQL) contra un
 * PostgreSQL embebido. También comprueba que la aplicación completa arranca (antes contextLoads)
 * y que schema.sql crea la extensión unaccent.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiConPostgresTest {

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry propiedades) {
        PostgresEmbebido.registrar(propiedades);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PokemonRepository pokemonRepository;
    @Autowired
    private MovimientoRepository movimientoRepository;
    @Autowired
    private RutaRepository rutaRepository;
    @Autowired
    private TipoRepository tipoRepository;
    @Autowired
    private EvolucionRepository evolucionRepository;

    /**
     * Datos mínimos: Pikachu y Charmander (Kanto, gen 1), Cyndaquil (Johto, gen 2) que aparece
     * en DOS rutas de Kanto, y Flabébé (Kalos, con tilde en el nombre).
     */
    @BeforeEach
    void datos() {
        pokemonRepository.deleteAll();
        movimientoRepository.deleteAll();
        rutaRepository.deleteAll();
        tipoRepository.deleteAll();
        evolucionRepository.deleteAll();

        Tipo electric = tipoRepository.save(new Tipo("electric"));
        Tipo fire = tipoRepository.save(new Tipo("fire"));
        Tipo fairy = tipoRepository.save(new Tipo("fairy"));
        Ruta bosque = ruta("Bosque Verde", "Kanto", 1);
        Ruta ruta1 = ruta("Ruta 1", "Kanto", 2);
        Ruta ruta29 = ruta("Ruta 29", "Johto", 3);

        pokemon(25, "pikachu", "Pikachu", 1, "Kanto", "Inicial", 90, List.of(electric), List.of(bosque, ruta29));
        pokemon(4, "charmander", "Charmander", 1, "Kanto", "Inicial", 65, List.of(fire), List.of());
        pokemon(155, "cyndaquil", "Cyndaquil", 2, "Johto", "Inicial", 65, List.of(fire), List.of(bosque, ruta1));
        pokemon(669, "flabebe", "Flabébé", 6, "Kalos", "Salvaje", 42, List.of(fairy), List.of());

        movimiento("psychic", "Psíquico", "Especial", 90);
        movimiento("thunder-punch", "Puño Trueno", "Físico", 75);
        movimiento("fire-punch", "Puño Fuego", "Físico", 75);
    }

    private Ruta ruta(String nombre, String region, int idPokeapi) {
        Ruta r = new Ruta();
        r.setNombre(nombre);
        r.setRegion(region);
        r.setIdPokeapi(idPokeapi);
        return rutaRepository.save(r);
    }

    private void pokemon(int id, String nombre, String nombreEs, int generacion, String region, String obtencion,
                         int velocidad, List<Tipo> tipos, List<Ruta> rutas) {
        Pokemon p = new Pokemon();
        p.setIdPokedex(id);
        p.setNombre(nombre);
        p.setNombreEs(nombreEs);
        p.setGeneracion(generacion);
        p.setRegion(region);
        p.setTipoObtencion(obtencion);
        p.setVelocidad(velocidad);
        p.setTipos(tipos);
        p.setRutas(rutas);
        pokemonRepository.save(p);
    }

    private void movimiento(String nombre, String nombreEs, String categoria, int potencia) {
        Movimiento m = new Movimiento();
        m.setNombre(nombre);
        m.setNombreEs(nombreEs);
        m.setCategoria(categoria);
        m.setPotencia(potencia);
        m.setEsCompetitivo(potencia > 80);
        movimientoRepository.save(m);
    }

    // ---------- F1 · listado ----------

    @Test
    void listadoOrdenadoPorIdPokedexConRelacionesCargadas() throws Exception {
        mvc.perform(get("/api/pokemons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].idPokedex", contains(4, 25, 155, 669)))
                .andExpect(jsonPath("$.content[1].tipos[0].nombre").value("electric"))
                .andExpect(jsonPath("$.content[1].rutas.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(4));
    }

    @Test
    void sortPorUnCampoInexistenteResponde400() throws Exception {
        mvc.perform(get("/api/pokemons?sort=foo")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/pokemons/buscar?nombre=pika&sort=foo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("'foo'")));
        mvc.perform(get("/api/movimientos?sort=foo")).andExpect(status().isBadRequest());
    }

    // ---------- F2 · filtros de un solo valor ----------

    @Test
    void regionIncluyeNativosYLosQueAparecenEnSusRutasSinDuplicados() throws Exception {
        // Cyndaquil aparece en dos rutas de Kanto: debe salir una sola vez
        mvc.perform(get("/api/pokemons/region/kanto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].idPokedex", containsInAnyOrder(4, 25, 155)))
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void tipoSinImportarMayusculas() throws Exception {
        mvc.perform(get("/api/pokemons/tipo/FIRE"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(4, 155)));
    }

    // ---------- F6 / F9 · nombres en español y sin tildes ----------

    @Test
    void buscarPokemonSinImportarTildesNiMayusculas() throws Exception {
        mvc.perform(get("/api/pokemons/buscar?nombre=pikachú"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(25)));
        mvc.perform(get("/api/pokemons/buscar?nombre=PIKA"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(25)));
        mvc.perform(get("/api/pokemons/buscar?nombre=flabébé"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(669)));
    }

    @Test
    void buscarMovimientoPorNombreEnEspanolSinTildes() throws Exception {
        mvc.perform(get("/api/movimientos/buscar?nombre=trueno"))
                .andExpect(jsonPath("$.content[*].nombre", contains("thunder-punch")));
        mvc.perform(get("/api/movimientos/buscar?nombre=psiquico"))
                .andExpect(jsonPath("$.content[*].nombre", contains("psychic")));
        mvc.perform(get("/api/movimientos/buscar?nombre=psíquico"))
                .andExpect(jsonPath("$.content[*].nombre", contains("psychic")));
        // ñ = n, y el total sale del countQuery
        mvc.perform(get("/api/movimientos/buscar?nombre=puno&size=1"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/movimientos/buscar?nombre=puño&size=1"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    // ---------- F4 · movimientos ----------

    @Test
    void competitivosYCategoria() throws Exception {
        mvc.perform(get("/api/movimientos/competitivos"))
                .andExpect(jsonPath("$.content[*].nombre", contains("psychic")));
        mvc.perform(get("/api/movimientos/categoria/físico"))
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    // ---------- F8 · filtros combinados ----------

    @Test
    void filtrosCombinadosConY() throws Exception {
        mvc.perform(get("/api/pokemons?tipo=fire&generacion=1"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(4)));
        mvc.perform(get("/api/pokemons?nombre=pikachú&generacion=1"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(25)));
        mvc.perform(get("/api/pokemons?region=kanto&tipo=fire"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(4, 155)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/pokemons?obtencion=inicial&generacion=2"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(155)));
    }

    @Test
    void filtrosCombinadosConOrden() throws Exception {
        mvc.perform(get("/api/pokemons?region=kanto&sort=velocidad,desc"))
                .andExpect(jsonPath("$.content[0].idPokedex").value(25));
    }

    @Test
    void tipoORegionInexistenteDevuelveListaVacia() throws Exception {
        mvc.perform(get("/api/pokemons?tipo=sombra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(get("/api/pokemons?region=narnia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    // ---------- F14 · filtro por dos tipos ----------

    /** Charizard (#6) y Moltres (#146) son fuego/volador; Pidgey (#16), normal/volador. */
    private void datosDeDosTipos() {
        Tipo fire = tipoRepository.findByNombre("fire");
        Tipo flying = tipoRepository.save(new Tipo("flying"));
        Tipo normal = tipoRepository.save(new Tipo("normal"));
        Ruta bosque = rutaRepository.findByIdPokeapi(1).orElseThrow();
        Ruta ruta1 = rutaRepository.findByIdPokeapi(2).orElseThrow();
        pokemon(6, "charizard", "Charizard", 1, "Kanto", "Evolución", 100, List.of(fire, flying), List.of(bosque, ruta1));
        pokemon(146, "moltres", "Moltres", 1, "Kanto", "Legendario", 90, List.of(fire, flying), List.of());
        pokemon(16, "pidgey", "Pidgey", 1, "Kanto", "Salvaje", 56, List.of(normal, flying), List.of(ruta1));
    }

    @Test
    void dosTiposDevuelveSoloLosQueTienenAmbos() throws Exception {
        datosDeDosTipos();
        mvc.perform(get("/api/pokemons?tipo=fire&tipo=flying"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].idPokedex", contains(6, 146)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        // El orden de los tipos no importa
        mvc.perform(get("/api/pokemons?tipo=flying&tipo=FIRE"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(6, 146)));
    }

    @Test
    void unSoloTipoSigueIgualYLosRepetidosOVaciosCuentanUnaVez() throws Exception {
        datosDeDosTipos();
        String unTipo = mvc.perform(get("/api/pokemons?tipo=fire")).andReturn().getResponse().getContentAsString();
        String repetido = mvc.perform(get("/api/pokemons?tipo=fire&tipo=fire")).andReturn().getResponse().getContentAsString();
        String conVacio = mvc.perform(get("/api/pokemons?tipo=&tipo=fire")).andReturn().getResponse().getContentAsString();
        String endpoint = mvc.perform(get("/api/pokemons/tipo/fire")).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(repetido).isEqualTo(unTipo);
        org.assertj.core.api.Assertions.assertThat(conVacio).isEqualTo(unTipo);
        org.assertj.core.api.Assertions.assertThat(unTipo).isEqualTo(endpoint);
        mvc.perform(get("/api/pokemons?tipo=fire"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(4, 6, 146, 155)));
    }

    @Test
    void dosTiposSeCombinanConOtrosFiltrosYOrdenSinDuplicados() throws Exception {
        datosDeDosTipos();
        // Charizard está en dos rutas de Kanto: debe salir una sola vez
        mvc.perform(get("/api/pokemons?tipo=fire&tipo=flying&region=kanto&generacion=1&sort=velocidad,desc"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(6, 146)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/pokemons?tipo=fire&tipo=flying&obtencion=legendario"))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(146)));
    }

    @Test
    void cadaFiltroDaLoMismoQueSuEndpointDeUnSoloFiltro() throws Exception {
        String porParametro = mvc.perform(get("/api/pokemons?region=kanto")).andReturn().getResponse().getContentAsString();
        String porEndpoint = mvc.perform(get("/api/pokemons/region/kanto")).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(porParametro).isEqualTo(porEndpoint);
    }

    // ---------- F5 · rutas ----------

    @Test
    void pokemonsDeUnaRuta() throws Exception {
        Integer idBosque = rutaRepository.findByIdPokeapi(1).orElseThrow().getIdRuta();

        mvc.perform(get("/api/pokemons/ruta/" + idBosque))
                .andExpect(jsonPath("$.content[*].idPokedex", contains(25, 155)));
        mvc.perform(get("/api/rutas/region/kanto"))
                .andExpect(jsonPath("$.content[*].nombre", contains("Bosque Verde", "Ruta 1")));
    }
}
