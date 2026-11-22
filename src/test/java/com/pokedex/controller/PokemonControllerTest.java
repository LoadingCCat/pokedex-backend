package com.pokedex.controller;

import com.pokedex.entity.Pokemon;
import com.pokedex.exception.ConflictException;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.service.EvolucionService;
import com.pokedex.service.EvolucionService.EtapaEvolucion;
import com.pokedex.service.PokemonService;
import com.pokedex.service.ProgresoImportacion;
import com.pokedex.ConSeguridad;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios de F1, F2, F7, F8 y F10 que dependen del controller (validación, formato,
 * códigos HTTP). Los repositorios y servicios están simulados: no se necesita PostgreSQL.
 */
@WebMvcTest(PokemonController.class)
@ConSeguridad
// Las pruebas de escritura de F1–F14 corren como ADMIN; las de 401/403 están en SeguridadControllerTest (F15)
@WithMockUser(roles = "ADMIN")
class PokemonControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PokemonRepository pokemonRepository;
    @MockitoBean
    private RutaRepository rutaRepository;
    @MockitoBean
    private MovimientoRepository movimientoRepository;
    @MockitoBean
    private PokemonService pokemonService;
    @MockitoBean
    private EvolucionService evolucionService;
    // F15: sus endpoints se prueban en GestionPokemonControllerTest
    @MockitoBean
    private com.pokedex.service.GestionPokemonService gestionPokemonService;

    static Pokemon pikachu() {
        Pokemon p = new Pokemon();
        p.setIdPokedex(25);
        p.setNombre("pikachu");
        p.setNombreEs("Pikachu");
        p.setGeneracion(1);
        p.setRegion("Kanto");
        p.setTipoObtencion("Inicial");
        p.setPs(35);
        p.setAtaque(55);
        p.setDefensa(40);
        p.setAtaqueEspecial(50);
        p.setDefensaEspecial(50);
        p.setVelocidad(90);
        p.setTipos(List.of());
        p.setRutas(List.of());
        return p;
    }

    private static Page<Pokemon> paginaCon(Pokemon... pokemons) {
        return new PageImpl<>(List.of(pokemons));
    }

    // ---------- F1 · listado, paginación y ficha ----------

    @Test
    void listadoUsaPaginasDe20OrdenadasPorIdPokedexYFormatoContentPage() throws Exception {
        when(pokemonRepository.findAll(any(Pageable.class))).thenReturn(paginaCon(pikachu()));

        mvc.perform(get("/api/pokemons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].idPokedex").value(25))
                .andExpect(jsonPath("$.page.size").exists())
                .andExpect(jsonPath("$.page.number").exists())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").exists())
                .andExpect(jsonPath("$.pageable").doesNotExist());

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(pokemonRepository).findAll(pagina.capture());
        assertThat(pagina.getValue().getPageSize()).isEqualTo(20);
        assertThat(pagina.getValue().getSort()).isEqualTo(Sort.by("idPokedex"));
    }

    @Test
    void elTamanoDePaginaSeLimitaA200() throws Exception {
        when(pokemonRepository.findAll(any(Pageable.class))).thenReturn(paginaCon());

        mvc.perform(get("/api/pokemons?size=500")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(pokemonRepository).findAll(pagina.capture());
        assertThat(pagina.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void fichaIncluyeEstadisticasYTotalCalculadoPeroNoLosMovimientos() throws Exception {
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(pikachu()));

        mvc.perform(get("/api/pokemons/25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("pikachu"))
                .andExpect(jsonPath("$.nombreEs").value("Pikachu"))
                .andExpect(jsonPath("$.totalEstadisticas").value(320))
                .andExpect(jsonPath("$.tipoObtencion").value("Inicial"))
                .andExpect(jsonPath("$.movimientos").doesNotExist());
    }

    @Test
    void pokemonInexistenteResponde404ConElFormatoDeError() throws Exception {
        when(pokemonRepository.findById(9999)).thenReturn(Optional.empty());

        mvc.perform(get("/api/pokemons/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.mensaje").value("Pokémon con id 9999 no existe"))
                .andExpect(jsonPath("$.path").value("/api/pokemons/9999"));
    }

    @Test
    void idNoNumericoResponde400() throws Exception {
        mvc.perform(get("/api/pokemons/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("'id'")));
    }

    // T-08 · F7: «GET /api/pokemons/abc/evoluciones responde 400»
    @Test
    void idNoNumericoEnEvolucionesResponde400() throws Exception {
        mvc.perform(get("/api/pokemons/abc/evoluciones"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("'id'")));
        org.mockito.Mockito.verifyNoInteractions(evolucionService);
    }

    @Test
    void urlInexistenteResponde404() throws Exception {
        mvc.perform(get("/api/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La ruta solicitada no existe"));
    }

    @Test
    void metodoNoPermitidoResponde405() throws Exception {
        // Desde F15, DELETE /api/pokemons/{id} existe: el ejemplo pasa a una ruta que solo acepta GET
        mvc.perform(delete("/api/pokemons/25/movimientos"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void errorInesperadoResponde500SinExponerElDetalle() throws Exception {
        when(pokemonRepository.findById(1)).thenThrow(new RuntimeException("SELECT * FROM secreto"));

        mvc.perform(get("/api/pokemons/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value(not(containsString("secreto"))));
    }

    // ---------- F4 · movimientos de un Pokémon ----------

    @Test
    void movimientosDeUnPokemonInexistenteResponde404() throws Exception {
        when(pokemonRepository.existsById(9999)).thenReturn(false);

        mvc.perform(get("/api/pokemons/9999/movimientos")).andExpect(status().isNotFound());
        verify(movimientoRepository, never()).buscarPorPokemon(anyInt());
    }

    @Test
    void movimientosDeUnPokemonSonUnArregloSinPaginar() throws Exception {
        when(pokemonRepository.existsById(25)).thenReturn(true);
        when(movimientoRepository.buscarPorPokemon(25)).thenReturn(List.of());

        mvc.perform(get("/api/pokemons/25/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ---------- F2 · filtros de un solo valor ----------

    @Test
    void busquedaPorNombreVaciaOFaltanteResponde400() throws Exception {
        mvc.perform(get("/api/pokemons/buscar?nombre=  ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/pokemons/buscar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Falta el parámetro obligatorio 'nombre'"));
    }

    @Test
    void busquedaPorNombreQuitaEspacios() throws Exception {
        when(pokemonRepository.buscarPorNombre(eq("pika"), any())).thenReturn(paginaCon(pikachu()));

        mvc.perform(get("/api/pokemons/buscar?nombre= pika "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("pikachu"));
    }

    @Test
    void generacionFueraDeRangoResponde400() throws Exception {
        mvc.perform(get("/api/pokemons/generacion/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/pokemons/generacion/10")).andExpect(status().isBadRequest());
        verify(pokemonRepository, never()).findByGeneracion(anyInt(), any());
    }

    @Test
    void obtencionAceptaElValorSinTilde() throws Exception {
        when(pokemonRepository.findByTipoObtencionIgnoreCase(eq("Fósil"), any())).thenReturn(paginaCon());

        mvc.perform(get("/api/pokemons/obtencion/fosil")).andExpect(status().isOk());
        verify(pokemonRepository).findByTipoObtencionIgnoreCase(eq("Fósil"), any());
    }

    @Test
    void obtencionInvalidaResponde400ConLosValoresValidos() throws Exception {
        mvc.perform(get("/api/pokemons/obtencion/robado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("Legendario")));
    }

    @Test
    void pokemonsDeUnaRutaInexistenteResponde404() throws Exception {
        when(rutaRepository.existsById(999)).thenReturn(false);

        mvc.perform(get("/api/pokemons/ruta/999")).andExpect(status().isNotFound());
    }

    // ---------- F8 · filtros combinados ----------

    @Test
    void filtrosVaciosSeIgnoranYRespondeComoElListadoNormal() throws Exception {
        when(pokemonRepository.findAll(any(Pageable.class))).thenReturn(paginaCon());

        mvc.perform(get("/api/pokemons?tipo=&nombre=  &region=&obtencion="))
                .andExpect(status().isOk());

        verify(pokemonRepository).findAll(any(Pageable.class));
        verify(pokemonRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void conAlgunFiltroSeUsaLaConsultaCombinada() throws Exception {
        when(pokemonRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(paginaCon());

        mvc.perform(get("/api/pokemons?tipo=fire&generacion=1&sort=velocidad,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(pokemonRepository).findAll(any(Specification.class), pagina.capture());
        assertThat(pagina.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "velocidad"));
    }

    // ---------- F14 · filtro por dos tipos ----------

    @Test
    void tresTiposDistintosResponde400() throws Exception {
        mvc.perform(get("/api/pokemons?tipo=fire&tipo=flying&tipo=water"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value(containsString("como mucho 2 tipos")));
        verify(pokemonRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tiposRepetidosOVaciosNoCuentanParaElLimite() throws Exception {
        when(pokemonRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(paginaCon());

        // fire y FIRE son el mismo tipo, y el vacío se ignora: quedan 2
        mvc.perform(get("/api/pokemons?tipo=fire&tipo=FIRE&tipo=&tipo=flying"))
                .andExpect(status().isOk());
        verify(pokemonRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void soloTiposVaciosRespondeComoElListadoNormal() throws Exception {
        when(pokemonRepository.findAll(any(Pageable.class))).thenReturn(paginaCon());

        mvc.perform(get("/api/pokemons?tipo=&tipo=  ")).andExpect(status().isOk());
        verify(pokemonRepository).findAll(any(Pageable.class));
    }

    @Test
    void filtroGeneracionInvalidoResponde400() throws Exception {
        mvc.perform(get("/api/pokemons?generacion=10")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/pokemons?generacion=abc")).andExpect(status().isBadRequest());
    }

    @Test
    void filtroObtencionInvalidoResponde400() throws Exception {
        mvc.perform(get("/api/pokemons?obtencion=robado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("Valores")));
    }

    // ---------- F7 · cadena evolutiva ----------

    @Test
    void evolucionesDevuelveElArbolDelServicio() throws Exception {
        EtapaEvolucion vaporeon = new EtapaEvolucion(134, "vaporeon", "Vaporeon", null, "Usando Piedra Agua", List.of());
        when(evolucionService.obtenerCadena(134))
                .thenReturn(new EtapaEvolucion(133, "eevee", "Eevee", null, null, List.of(vaporeon)));

        mvc.perform(get("/api/pokemons/134/evoluciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPokedex").value(133))
                .andExpect(jsonPath("$.condicion").doesNotExist())
                .andExpect(jsonPath("$.evoluciones[0].condicion").value("Usando Piedra Agua"))
                .andExpect(jsonPath("$.evoluciones[0].evoluciones").isArray());
    }

    @Test
    void evolucionesDeUnPokemonInexistenteResponde404() throws Exception {
        when(evolucionService.obtenerCadena(9999)).thenThrow(new ResourceNotFoundException("Pokémon", 9999));

        mvc.perform(get("/api/pokemons/9999/evoluciones")).andExpect(status().isNotFound());
    }

    // ---------- F3 / F10 · importación ----------

    @Test
    void parametrosDeImportacionInvalidosResponden400SinIniciarNada() throws Exception {
        mvc.perform(post("/api/pokemons/importar-rango?total=0")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/pokemons/importar-rango?total=1026")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/pokemons/importar-rango?total=5&movimientos=-1")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/pokemons/importar-rango?total=5&movimientos=901")).andExpect(status().isBadRequest());
        verify(pokemonService, never()).iniciarImportacion(anyInt(), anyInt());
    }

    @Test
    void importarRespondeAlInstanteCon202() throws Exception {
        when(pokemonService.iniciarImportacion(20, 0)).thenReturn(enCurso(20));

        mvc.perform(post("/api/pokemons/importar-rango?total=20&movimientos=0"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(containsString("/api/pokemons/importacion")))
                .andExpect(jsonPath("$.progreso.estado").value("en-curso"))
                .andExpect(jsonPath("$.progreso.total").value(20));
    }

    @Test
    void importarTambienAceptaGet() throws Exception {
        when(pokemonService.iniciarImportacion(50, 50)).thenReturn(enCurso(50));

        mvc.perform(get("/api/pokemons/importar-todo")).andExpect(status().isAccepted());
    }

    @Test
    void importacionEnCursoResponde409() throws Exception {
        when(pokemonService.iniciarImportacion(anyInt(), anyInt()))
                .thenThrow(new ConflictException("Ya hay una importación en curso. Espera a que termine."));

        mvc.perform(post("/api/pokemons/importar-rango?total=5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya hay una importación en curso. Espera a que termine."));
    }

    @Test
    void progresoDeLaImportacion() throws Exception {
        when(pokemonService.progresoImportacion()).thenReturn(new ProgresoImportacion().instantanea());

        mvc.perform(get("/api/pokemons/importacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("sin-iniciar"))
                .andExpect(jsonPath("$.resultados").doesNotExist());
    }

    private static ProgresoImportacion.Estado enCurso(int total) {
        ProgresoImportacion progreso = new ProgresoImportacion();
        progreso.iniciar(total);
        return progreso.instantanea();
    }

    // ---------- F15 · sin sesión ----------

    @Test
    @WithAnonymousUser
    void sinSesionLosGetDePokemonSonPublicos() throws Exception {
        when(pokemonRepository.findAll(any(Pageable.class))).thenReturn(paginaCon());
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(new Pokemon()));
        when(pokemonService.progresoImportacion()).thenReturn(new ProgresoImportacion().instantanea());

        mvc.perform(get("/api/pokemons")).andExpect(status().isOk());
        mvc.perform(get("/api/pokemons/25")).andExpect(status().isOk());
        mvc.perform(get("/api/pokemons/importacion")).andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void sinSesionLaImportacionResponde401AunqueSeaGet() throws Exception {
        mvc.perform(get("/api/pokemons/importar-rango?total=1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pokemons/importar-todo")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pokemons/importar-rango?total=1")).andExpect(status().isUnauthorized());
        verify(pokemonService, never()).iniciarImportacion(anyInt(), anyInt());
    }
}
