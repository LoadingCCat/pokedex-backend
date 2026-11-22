package com.pokedex;

import com.pokedex.entity.Movimiento;
import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Ruta;
import com.pokedex.entity.Tipo;
import com.pokedex.entity.Usuario;
import com.pokedex.service.SimilaresService;
import com.pokedex.repository.EvolucionRepository;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.PokemonExcluidoRepository;
import com.pokedex.repository.RutaExcluidaRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.repository.TipoRepository;
import com.pokedex.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        // F15: secreto y administrador inicial solo de prueba (pisan los de application-local)
        propiedades.add("pokedex.seguridad.jwt-secreto", () -> ConSeguridad.SECRETO_DE_PRUEBA);
        propiedades.add("pokedex.seguridad.admin-usuario", () -> "admin-prueba");
        propiedades.add("pokedex.seguridad.admin-contrasena", () -> "clave-de-prueba");
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
        rutaExcluidaRepository.deleteAll();
        pokemonExcluidoRepository.deleteAll();

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

    // S-03 (interventoría): el 400 de sort no revela nombres de clase ni de tipo internos
    @Test
    void sortInvalidoNoRevelaNombresInternos() throws Exception {
        for (String url : new String[]{"/api/pokemons?sort=foo", "/api/pokemons/buscar?nombre=pika&sort=foo",
                "/api/movimientos?sort=foo", "/api/rutas?sort=foo"}) {
            mvc.perform(get(url))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(containsString("'sort'")))
                    .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.not(containsString("No property"))))
                    .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.not(containsString("type"))))
                    .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.not(containsString("Pokemon"))));
        }
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

    // ---------- F15 · autenticación de punta a punta ----------

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private RutaExcluidaRepository rutaExcluidaRepository;
    @Autowired
    private PokemonExcluidoRepository pokemonExcluidoRepository;

    private String tokenAdmin() throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\": \"admin-prueba\", \"contrasena\": \"clave-de-prueba\"}"))
                .andReturn().getResponse().getContentAsString();
        return respuesta.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void borrarUnaRutaLaQuitaDeSusPokemonYLaExcluyeDeLaReimportacion() throws Exception {
        Integer idBosque = rutaRepository.findByIdPokeapi(1).orElseThrow().getIdRuta();

        mvc.perform(delete("/api/rutas/" + idBosque).header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/rutas/" + idBosque)).andExpect(status().isNotFound());
        // Pikachu estaba en el Bosque y en la Ruta 29: le queda una
        mvc.perform(get("/api/pokemons/25"))
                .andExpect(jsonPath("$.rutas[*].nombre", contains("Ruta 29")));
        org.assertj.core.api.Assertions.assertThat(rutaExcluidaRepository.existsById(1)).isTrue();
    }

    @Test
    void elAdministradorInicialIniciaSesionYSoloConSuTokenSePuedeEscribir() throws Exception {
        // Lo creó AdministradorInicial al arrancar, con la contraseña cifrada
        Usuario admin = usuarioRepository.findByUsuario("admin-prueba").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(admin.getContrasena()).isNotEqualTo("clave-de-prueba").startsWith("$2");

        String ruta = """
                {"nombre": "Ruta de prueba F15", "region": "Kanto", "descripcion": "x"}""";
        mvc.perform(post("/api/rutas").contentType(MediaType.APPLICATION_JSON).content(ruta))
                .andExpect(status().isUnauthorized());

        String respuesta = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario": "admin-prueba", "contrasena": "clave-de-prueba"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = respuesta.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/rutas").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(ruta))
                .andExpect(status().isCreated());
        // Los GET siguen sin pedir token
        mvc.perform(get("/api/rutas/region/kanto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void editarYBorrarUnPokemonConElTokenDeAdmin() throws Exception {
        String token = "Bearer " + tokenAdmin();
        String cuerpo = """
                {"nombreEs": "Pikachu Editado", "ps": 35, "ataque": 60, "defensa": 40, "ataqueEspecial": 50,
                 "defensaEspecial": 50, "velocidad": 95, "altura": 0.4, "peso": 6, "tipoObtencion": "Inicial",
                 "tipos": ["electric", "fairy"]}""";

        mvc.perform(put("/api/pokemons/25").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());
        mvc.perform(get("/api/pokemons/25"))
                .andExpect(jsonPath("$.nombreEs").value("Pikachu Editado"))
                .andExpect(jsonPath("$.tipos[*].nombre", containsInAnyOrder("electric", "fairy")))
                .andExpect(jsonPath("$.rutas.length()").value(2)) // sin "rutas" en el cuerpo, se conservan
                .andExpect(jsonPath("$.editadoAMano").value(true));

        // Cyndaquil (#155) tiene tipo y dos rutas: al borrarlo se van sus filas de pokemon_tipo y pokemon_ruta
        mvc.perform(delete("/api/pokemons/155").header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/pokemons/155")).andExpect(status().isNotFound());
        mvc.perform(get("/api/pokemons/tipo/fire")).andExpect(jsonPath("$.content[*].idPokedex", contains(4)));
        org.assertj.core.api.Assertions.assertThat(pokemonExcluidoRepository.existsById(155)).isTrue();
    }

    // ---------- F20 · similares ----------

    @Autowired
    private SimilaresService similaresService;

    /** Pokémon con las 6 estadísticas: cinco de 50 y PS para que la suma dé `total`. */
    private void conTotal(int id, String nombre, int generacion, int total, Tipo... tipos) {
        Pokemon p = new Pokemon();
        p.setIdPokedex(id);
        p.setNombre(nombre);
        p.setNombreEs(nombre);
        p.setGeneracion(generacion);
        p.setPs(total - 250);
        p.setAtaque(50);
        p.setDefensa(50);
        p.setAtaqueEspecial(50);
        p.setDefensaEspecial(50);
        p.setVelocidad(50);
        p.setTipos(List.of(tipos));
        p.setRutas(List.of());
        pokemonRepository.save(p);
    }

    private Tipo tipo(String nombre) {
        Tipo t = tipoRepository.findByNombre(nombre);
        return t != null ? t : tipoRepository.save(new Tipo(nombre));
    }

    private List<Integer> ids(List<SimilaresService.Similar> similares) {
        return similares.stream().map(s -> s.pokemon().getIdPokedex()).toList();
    }

    @Test
    void charmanderTiene3SimilaresConTipoCompartidoOrdenadosPorDiferenciaDeTotal() {
        pokemonRepository.deleteAll();
        Tipo fire = tipo("fire");
        Tipo water = tipo("water");
        conTotal(4, "charmander", 1, 309, fire);
        conTotal(37, "vulpix", 1, 400, fire);          // diferencia 91
        conTotal(255, "torchic", 3, 320, fire);        // 11
        conTotal(390, "chimchar", 4, 310, fire);       // 1
        conTotal(155, "cyndaquil", 2, 309, fire);      // 0
        conTotal(7, "squirtle", 1, 309, water);        // diferencia 0, pero otro tipo: no entra

        List<SimilaresService.Similar> similares = similaresService.similaresDe(4);

        org.assertj.core.api.Assertions.assertThat(ids(similares)).containsExactly(155, 390, 255);
        org.assertj.core.api.Assertions.assertThat(similares)
                .allSatisfy(s -> org.assertj.core.api.Assertions.assertThat(s.pokemon().getTipos())
                        .extracting(Tipo::getNombre).contains("fire"));
        org.assertj.core.api.Assertions.assertThat(similares.stream().map(SimilaresService.Similar::motivo))
                .containsExactly("Comparte el tipo Fuego y tiene el mismo total",
                        "Comparte el tipo Fuego y su total difiere en 1",
                        "Comparte el tipo Fuego y su total difiere en 11");
    }

    @Test
    void conMenosDe3ConTipoCompartidoSeCompletaConElTotalMasCercano() {
        pokemonRepository.deleteAll();
        Tipo fire = tipo("fire");
        Tipo water = tipo("water");
        Tipo grass = tipo("grass");
        conTotal(4, "charmander", 1, 309, fire);
        conTotal(37, "vulpix", 1, 359, fire);          // único con fuego (diferencia 50)
        conTotal(7, "squirtle", 1, 314, water);        // 5
        conTotal(1, "bulbasaur", 1, 329, grass);       // 20
        conTotal(2, "ivysaur", 1, 409, grass);         // 100: no entra
        conTotal(3, "venusaur", 1, 509, grass);        // 200: no entra

        List<SimilaresService.Similar> similares = similaresService.similaresDe(4);

        // El de tipo compartido va primero aunque esté más lejos; luego los de total más cercano
        org.assertj.core.api.Assertions.assertThat(ids(similares)).containsExactly(37, 7, 1);
        org.assertj.core.api.Assertions.assertThat(ids(similares)).doesNotHaveDuplicates().doesNotContain(4);
        org.assertj.core.api.Assertions.assertThat(similares.get(0).motivo()).isEqualTo("Comparte el tipo Fuego y su total difiere en 50");
        org.assertj.core.api.Assertions.assertThat(similares.get(1).motivo()).isEqualTo("No comparte tipo, pero su total difiere en 5");
        org.assertj.core.api.Assertions.assertThat(similares.get(2).motivo()).isEqualTo("No comparte tipo, pero su total difiere en 20");
    }

    @Test
    void sinNingunoConTipoCompartidoDevuelveIgualLos3MasCercanos() {
        pokemonRepository.deleteAll();
        Tipo fire = tipo("fire");
        Tipo water = tipo("water");
        conTotal(4, "charmander", 1, 309, fire);
        conTotal(7, "squirtle", 1, 309, water);
        conTotal(8, "wartortle", 1, 330, water);
        conTotal(9, "blastoise", 1, 300, water);
        conTotal(54, "psyduck", 1, 500, water);

        org.assertj.core.api.Assertions.assertThat(ids(similaresService.similaresDe(4))).containsExactly(7, 9, 8);
    }

    @Test
    void elEmpateDeTotalSeDesempataPorMismaGeneracionYLuegoPorIdPokedex() {
        pokemonRepository.deleteAll();
        Tipo fire = tipo("fire");
        conTotal(4, "charmander", 1, 309, fire);
        // Todos a diferencia 10 (299 o 319): la generación 1 va primero, y dentro, por idPokedex
        conTotal(500, "gen2-a", 2, 319, fire);
        conTotal(300, "gen2-b", 2, 299, fire);
        conTotal(200, "gen1-b", 1, 319, fire);
        conTotal(100, "gen1-a", 1, 299, fire);

        org.assertj.core.api.Assertions.assertThat(ids(similaresService.similaresDe(4))).containsExactly(100, 200, 300);
    }

    @Test
    void unPokemonConDosTiposCompartidosSaleUnaSolaVezYElMotivoNombraLosDos() {
        pokemonRepository.deleteAll();
        Tipo fire = tipo("fire");
        Tipo flying = tipo("flying");
        conTotal(6, "charizard", 1, 534, fire, flying);
        conTotal(146, "moltres", 1, 580, fire, flying);   // 46
        conTotal(663, "talonflame", 6, 499, fire, flying); // 35
        conTotal(16, "pidgey", 1, 251, flying);           // 283, pero comparte volador
        conTotal(4, "charmander", 1, 309, fire);          // 225

        List<SimilaresService.Similar> similares = similaresService.similaresDe(6);

        org.assertj.core.api.Assertions.assertThat(ids(similares)).containsExactly(663, 146, 4);
        org.assertj.core.api.Assertions.assertThat(similares.get(0).motivo())
                .isEqualTo("Comparte los tipos Fuego y Volador y su total difiere en 35");
    }

    @Test
    void losPokemonConEstadisticasIncompletasNoSonSimilares() {
        // @BeforeEach deja a Pikachu, Cyndaquil y Flabébé sin PS, ataque, etc.
        conTotal(5, "charmeleon", 1, 405, tipo("fire"));
        conTotal(37, "vulpix", 1, 300, tipo("fire"));
        Pokemon charmander = pokemonRepository.findById(4).orElseThrow();
        charmander.setPs(39);
        charmander.setAtaque(52);
        charmander.setDefensa(43);
        charmander.setAtaqueEspecial(60);
        charmander.setDefensaEspecial(50);
        charmander.setVelocidad(65); // 309
        pokemonRepository.save(charmander);

        org.assertj.core.api.Assertions.assertThat(ids(similaresService.similaresDe(4))).containsExactly(37, 5);
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
