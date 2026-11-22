package com.pokedex.controller;

import com.pokedex.ConSeguridad;
import com.pokedex.entity.Ruta;
import com.pokedex.repository.RutaExcluidaRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.service.PokemonService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S-02 (interventoría): POST y PUT de rutas validan el largo de cada campo contra el esquema
 * (`nombre` ≤ 100, `region` ≤ 50) y ponen un tope a `descripcion` (TEXT en la BD, 2000 aquí),
 * con 400 y `campos`. El cuerpo se recibe en un `record`: `idRuta` e `idPokeapi` se ignoran.
 */
@WebMvcTest(RutaController.class)
@ConSeguridad
@WithMockUser(roles = "ADMIN")
class RutaValidacionTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RutaRepository rutaRepository;
    @MockitoBean
    private PokemonService pokemonService;
    @MockitoBean
    private RutaExcluidaRepository rutaExcluidaRepository;

    private static String json(String nombre, String region, String descripcion) {
        return "{\"nombre\": \"" + nombre + "\", \"region\": \"" + region + "\", \"descripcion\": \""
                + descripcion + "\"}";
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return mvc.perform(post("/api/rutas").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions editar(String cuerpo) throws Exception {
        return mvc.perform(put("/api/rutas/5").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void existeLaRuta5() {
        Ruta r = new Ruta();
        r.setIdRuta(5);
        r.setNombre("Ruta 1");
        r.setRegion("Kanto");
        when(rutaRepository.findById(5)).thenReturn(Optional.of(r));
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void postConUnNombreDe101CaracteresResponde400ConElCampoNombre() throws Exception {
        crear(json("n".repeat(101), "Kanto", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());
        verify(rutaRepository, never()).save(any());
    }

    @Test
    void postConUnaRegionDe51CaracteresResponde400ConElCampoRegion() throws Exception {
        crear(json("Ruta 1", "r".repeat(51), ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.region").exists());
        verify(rutaRepository, never()).save(any());
    }

    @Test
    void postConUnaDescripcionDeMasDe2000CaracteresResponde400ConElCampoDescripcion() throws Exception {
        crear(json("Ruta 1", "Kanto", "d".repeat(2001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.descripcion").exists());
        verify(rutaRepository, never()).save(any());
    }

    @Test
    void putConCadaCampoDemasiadoLargoResponde400ConSuCampo() throws Exception {
        existeLaRuta5();

        editar(json("n".repeat(101), "Kanto", "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());
        editar(json("Ruta 1", "r".repeat(51), "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.region").exists());
        editar(json("Ruta 1", "Kanto", "d".repeat(2001))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.descripcion").exists());
        verify(rutaRepository, never()).save(any());
    }

    @Test
    void conExactamenteElLargoMaximoEnCadaCampoSiSeGuarda() throws Exception {
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(i -> i.getArgument(0));

        crear(json("n".repeat(100), "r".repeat(50), "d".repeat(2000))).andExpect(status().isCreated());
    }

    @Test
    void elCuerpoNoPuedeFijarElIdNiElIdPokeapi() throws Exception {
        when(rutaRepository.save(any(Ruta.class))).thenAnswer(i -> i.getArgument(0));

        crear("""
                {"idRuta": 99, "idPokeapi": 123, "nombre": "Ruta Prueba", "region": "Kanto"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPokeapi").doesNotExist());

        ArgumentCaptor<Ruta> guardada = ArgumentCaptor.forClass(Ruta.class);
        verify(rutaRepository).save(guardada.capture());
        assertThat(guardada.getValue().getIdRuta()).isNull();
        assertThat(guardada.getValue().getIdPokeapi()).isNull();
    }
}
