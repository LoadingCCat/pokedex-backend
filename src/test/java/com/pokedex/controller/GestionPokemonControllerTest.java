package com.pokedex.controller;

import com.pokedex.ConSeguridad;
import com.pokedex.entity.Pokemon;
import com.pokedex.entity.PokemonExcluido;
import com.pokedex.entity.Ruta;
import com.pokedex.entity.Tipo;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.PokemonExcluidoRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.repository.TipoRepository;
import com.pokedex.service.EvolucionService;
import com.pokedex.service.GestionPokemonService;
import com.pokedex.service.PokemonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F15 (B.32): crear, editar y borrar Pokémon, con validación por campo (RF-12). */
@WebMvcTest(PokemonController.class)
@ConSeguridad
@Import(GestionPokemonService.class)
@WithMockUser(roles = "ADMIN")
class GestionPokemonControllerTest {

    /** Un cuerpo válido para PUT; los de POST suman idPokedex, nombre y generacion. */
    private static final String VALIDO = """
            {"nombreEs": "Pikachu Editado", "ps": 35, "ataque": 60, "defensa": 40, "ataqueEspecial": 50,
             "defensaEspecial": 50, "velocidad": 95, "altura": 0.4, "peso": 6, "tipoObtencion": "inicial",
             "formaObtencion": "Se elige al empezar.", "tipos": ["electric"], "rutas": [3]}""";

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
    @MockitoBean
    private TipoRepository tipoRepository;
    @MockitoBean
    private PokemonExcluidoRepository pokemonExcluidoRepository;

    private final Ruta bosque = new Ruta();

    @BeforeEach
    void datos() {
        Tipo electric = new Tipo("electric");
        when(tipoRepository.findByNombre("electric")).thenReturn(electric);
        when(tipoRepository.findByNombre("flying")).thenReturn(new Tipo("flying"));
        bosque.setIdRuta(3);
        bosque.setNombre("Bosque Verde");
        bosque.setRegion("Kanto");
        when(rutaRepository.findAllById(List.of(3))).thenReturn(List.of(bosque));
        when(pokemonRepository.save(any(Pokemon.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static Pokemon pikachuGuardado() {
        Pokemon p = PokemonControllerTest.pikachu();
        p.setRutas(new ArrayList<>());
        return p;
    }

    private ResultActions editar(int id, String json) throws Exception {
        return mvc.perform(put("/api/pokemons/" + id).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions crear(String json) throws Exception {
        return mvc.perform(post("/api/pokemons").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---------- PUT ----------

    @Test
    void editarConDatosValidosLosGuardaYLoMarcaComoEditadoAMano() throws Exception {
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(pikachuGuardado()));

        editar(25, VALIDO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreEs").value("Pikachu Editado"))
                .andExpect(jsonPath("$.nombre").value("pikachu"))
                .andExpect(jsonPath("$.ataque").value(60))
                .andExpect(jsonPath("$.totalEstadisticas").value(330))
                .andExpect(jsonPath("$.tipoObtencion").value("Inicial"))
                .andExpect(jsonPath("$.tipos[0].nombre").value("electric"))
                .andExpect(jsonPath("$.rutas[0].nombre").value("Bosque Verde"))
                .andExpect(jsonPath("$.editadoAMano").value(true));
    }

    @Test
    void unaEstadisticaFueraDeRangoOConTextoResponde400ConElCampo() throws Exception {
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(pikachuGuardado()));

        editar(25, VALIDO.replace("\"ataque\": 60", "\"ataque\": 300"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value(containsString("ataque")))
                .andExpect(jsonPath("$.campos.ataque").value("Debe estar entre 1 y 255"));
        editar(25, VALIDO.replace("\"ataque\": 60", "\"ataque\": \"abc\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.ataque").value("Debe ser un número entero"));
        editar(25, VALIDO.replace("\"ps\": 35", "\"ps\": 0").replace("\"peso\": 6", "\"peso\": -1"))
                .andExpect(jsonPath("$.campos.ps").value("Debe estar entre 1 y 255"))
                .andExpect(jsonPath("$.campos.peso").value("No puede ser negativo"));
        verify(pokemonRepository, never()).save(any());
    }

    @Test
    void camposVaciosOValoresDesconocidosSeMarcanUnoPorUno() throws Exception {
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(pikachuGuardado()));

        editar(25, VALIDO.replace("\"Pikachu Editado\"", "\"  \"").replace("\"inicial\"", "\"robado\"")
                .replace("[\"electric\"]", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombreEs").value("Es obligatorio"))
                .andExpect(jsonPath("$.campos.tipoObtencion").value(containsString("Legendario")))
                .andExpect(jsonPath("$.campos.tipos").value("Elige 1 o 2 tipos"));
        when(rutaRepository.findAllById(List.of(3, 999))).thenReturn(List.of(bosque));
        editar(25, VALIDO.replace("[\"electric\"]", "[\"luz\"]").replace("[3]", "[3, 999]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tipos").value("Tipo desconocido: luz"))
                .andExpect(jsonPath("$.campos.rutas").value("No existen las rutas: 999"));
        editar(25, VALIDO.replace("[\"electric\"]", "[\"electric\", \"flying\", \"fire\"]"))
                .andExpect(jsonPath("$.campos.tipos").value("Un Pokémon tiene como mucho 2 tipos"));
        verify(pokemonRepository, never()).save(any());
    }

    @Test
    void editarUnPokemonInexistenteResponde404() throws Exception {
        when(pokemonRepository.findById(9999)).thenReturn(Optional.empty());

        editar(9999, VALIDO).andExpect(status().isNotFound());
    }

    @Test
    void sinCuerpoResponde400() throws Exception {
        mvc.perform(put("/api/pokemons/25")).andExpect(status().isBadRequest());
    }

    // ---------- POST ----------

    private static final String NUEVO = VALIDO.replace("{", """
            {"idPokedex": 2000, "nombre": "Pokemon-Propio", "generacion": 9, """);

    @Test
    void crearConUnNumeroLibreResponde201YLoSacaDeLosExcluidos() throws Exception {
        when(pokemonRepository.existsById(2000)).thenReturn(false);

        crear(NUEVO)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPokedex").value(2000))
                .andExpect(jsonPath("$.nombre").value("pokemon-propio"))
                .andExpect(jsonPath("$.region").value("Paldea"))
                .andExpect(jsonPath("$.editadoAMano").value(true));
        verify(pokemonExcluidoRepository).deleteById(2000);
    }

    @Test
    void crearConUnNumeroOcupadoResponde409() throws Exception {
        when(pokemonRepository.existsById(2000)).thenReturn(true);

        crear(NUEVO).andExpect(status().isConflict());
        verify(pokemonRepository, never()).save(any());
    }

    @Test
    void crearSinNumeroNombreNiGeneracionLosMarca() throws Exception {
        crear(VALIDO.replace("{", "{\"nombre\": \"Con Espacios\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.idPokedex").value("Es obligatorio"))
                .andExpect(jsonPath("$.campos.nombre").value(containsString("guiones")))
                .andExpect(jsonPath("$.campos.generacion").value("Es obligatorio"));
    }

    // ---------- DELETE ----------

    @Test
    void borrarLoEliminaYLoExcluyeDeLaReimportacion() throws Exception {
        Pokemon pikachu = pikachuGuardado();
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(pikachu));

        mvc.perform(delete("/api/pokemons/25")).andExpect(status().isNoContent());

        verify(pokemonRepository).delete(pikachu);
        ArgumentCaptor<PokemonExcluido> excluido = ArgumentCaptor.forClass(PokemonExcluido.class);
        verify(pokemonExcluidoRepository).save(excluido.capture());
        assertThat(excluido.getValue().getIdPokedex()).isEqualTo(25);
    }

    @Test
    void borrarUnPokemonInexistenteResponde404() throws Exception {
        when(pokemonRepository.findById(anyInt())).thenReturn(Optional.empty());

        mvc.perform(delete("/api/pokemons/9999")).andExpect(status().isNotFound());
        verify(pokemonExcluidoRepository, never()).save(any());
    }

    // ---------- Sin sesión ----------

    @Test
    @WithAnonymousUser
    void sinSesionNoSePuedeCrearEditarNiBorrar() throws Exception {
        crear(NUEVO).andExpect(status().isUnauthorized());
        editar(25, VALIDO).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/pokemons/25")).andExpect(status().isUnauthorized());
        verify(pokemonRepository, never()).save(any());
        verify(tipoRepository, never()).findByNombre(anyString());
    }
}
