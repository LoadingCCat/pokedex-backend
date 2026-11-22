package com.pokedex.controller;

import com.pokedex.entity.Ruta;
import com.pokedex.entity.RutaExcluida;
import com.pokedex.repository.RutaExcluidaRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.service.PokemonService;
import com.pokedex.ConSeguridad;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de F5 en el controller de rutas (sin PostgreSQL). */
@WebMvcTest(RutaController.class)
@ConSeguridad
// Las pruebas de escritura de F1–F14 corren como ADMIN; las de 401/403 están en SeguridadControllerTest (F15)
@WithMockUser(roles = "ADMIN")
class RutaControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RutaRepository rutaRepository;
    @MockitoBean
    private PokemonService pokemonService;
    @MockitoBean
    private RutaExcluidaRepository rutaExcluidaRepository;

    @Test
    void listadoPaginadoOrdenadoPorNombre() throws Exception {
        when(rutaRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/rutas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").exists());

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(rutaRepository).findAll(pagina.capture());
        assertThat(pagina.getValue().getSort()).isEqualTo(Sort.by("nombre"));
    }

    @Test
    void regionesEsUnArregloSinPaginar() throws Exception {
        when(pokemonService.listarRegiones()).thenReturn(List.of("Kanto", "Johto"));

        mvc.perform(get("/api/rutas/regiones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Kanto"));
    }

    @Test
    void rutaInexistenteResponde404() throws Exception {
        when(rutaRepository.findById(999)).thenReturn(Optional.empty());

        mvc.perform(get("/api/rutas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Ruta con id 999 no existe"));
    }

    @Test
    void crearFuerzaIdPokeapiYIdANull() throws Exception {
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(i -> i.getArgument(0));

        mvc.perform(post("/api/rutas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idRuta\":3,\"idPokeapi\":321,\"nombre\":\" Ruta Inventada \",\"region\":\"Kanto\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Ruta Inventada"))
                .andExpect(jsonPath("$.idPokeapi").doesNotExist())
                .andExpect(jsonPath("$.idRuta").doesNotExist());
    }

    @Test
    void crearSinNombreOSinRegionResponde400() throws Exception {
        mvc.perform(post("/api/rutas").contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"Kanto\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/rutas").contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Ruta X\"}"))
                .andExpect(status().isBadRequest());
        verify(rutaRepository, never()).save(any());
    }

    @Test
    void crearUnaRutaRepetidaEnLaMismaRegionResponde400() throws Exception {
        when(rutaRepository.existsByNombreIgnoreCaseAndRegionIgnoreCase(anyString(), anyString())).thenReturn(true);

        mvc.perform(post("/api/rutas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ruta 1\",\"region\":\"Kanto\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizarUnaRutaInexistenteResponde404() throws Exception {
        when(rutaRepository.findById(999)).thenReturn(Optional.empty());

        mvc.perform(put("/api/rutas/999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ruta X\",\"region\":\"Kanto\"}"))
                .andExpect(status().isNotFound());
    }

    // T-05 · F5: «region/kanto?size=200» cabe en una sola página (se acepta tamaño 200)
    @Test
    void rutasDeUnaRegionAceptanPaginasDe200() throws Exception {
        when(rutaRepository.findByRegionIgnoreCase(anyString(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/rutas/region/kanto?size=200")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(rutaRepository).findByRegionIgnoreCase(anyString(), pagina.capture());
        assertThat(pagina.getValue().getPageSize()).isEqualTo(200);
    }

    // T-05 · F5: «PUT /api/rutas/{id} cambia nombre, región y descripción, y no toca idPokeapi»
    @Test
    void actualizarCambiaNombreRegionYDescripcionSinTocarIdPokeapi() throws Exception {
        Ruta existente = rutaGuardada(3, 321);
        when(rutaRepository.findById(3)).thenReturn(Optional.of(existente));
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(i -> i.getArgument(0));

        // El cuerpo intenta colar un idPokeapi distinto: debe ignorarse
        mvc.perform(put("/api/rutas/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Bosque Nuevo\",\"region\":\"Johto\",\"descripcion\":\"Otra\",\"idPokeapi\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Bosque Nuevo"))
                .andExpect(jsonPath("$.region").value("Johto"))
                .andExpect(jsonPath("$.descripcion").value("Otra"))
                .andExpect(jsonPath("$.idPokeapi").value(321));
    }

    // ---------- F15 · borrar rutas ----------

    private static Ruta rutaGuardada(int id, Integer idPokeapi) {
        Ruta ruta = new Ruta();
        ruta.setIdRuta(id);
        ruta.setNombre("Bosque Verde");
        ruta.setRegion("Kanto");
        ruta.setIdPokeapi(idPokeapi);
        return ruta;
    }

    @Test
    void borrarUnLugarImportadoLoDesvinculaYLoExcluyeDeLaReimportacion() throws Exception {
        Ruta bosque = rutaGuardada(3, 321);
        when(rutaRepository.findById(3)).thenReturn(Optional.of(bosque));

        mvc.perform(delete("/api/rutas/3")).andExpect(status().isNoContent());

        verify(rutaRepository).desvincularDePokemons(3);
        ArgumentCaptor<RutaExcluida> excluida = ArgumentCaptor.forClass(RutaExcluida.class);
        verify(rutaExcluidaRepository).save(excluida.capture());
        assertThat(excluida.getValue().getIdPokeapi()).isEqualTo(321);
        verify(rutaRepository).delete(bosque);
    }

    @Test
    void borrarUnaRutaCreadaAManoNoAnotaNadaParaLaReimportacion() throws Exception {
        Ruta propia = rutaGuardada(700, null);
        when(rutaRepository.findById(700)).thenReturn(Optional.of(propia));

        mvc.perform(delete("/api/rutas/700")).andExpect(status().isNoContent());

        verify(rutaRepository).desvincularDePokemons(700);
        verify(rutaExcluidaRepository, never()).save(any());
        verify(rutaRepository).delete(propia);
    }

    @Test
    void borrarUnaRutaInexistenteResponde404() throws Exception {
        when(rutaRepository.findById(999)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/rutas/999")).andExpect(status().isNotFound());
        verify(rutaRepository, never()).desvincularDePokemons(any());
    }

    @Test
    @WithAnonymousUser
    void borrarSinSesionResponde401() throws Exception {
        mvc.perform(delete("/api/rutas/3")).andExpect(status().isUnauthorized());
        verify(rutaRepository, never()).delete(any(Ruta.class));
    }

    @Test
    void unaRutaSinNombreRespondeConElCampo() throws Exception {
        mvc.perform(post("/api/rutas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\" \",\"region\":\"Kanto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El nombre de la ruta es obligatorio"))
                .andExpect(jsonPath("$.campos.nombre").value("El nombre de la ruta es obligatorio"));
    }
}
