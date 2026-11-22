package com.pokedex.controller;

import com.pokedex.ConSeguridad;
import com.pokedex.entity.Movimiento;
import com.pokedex.repository.MovimientoRepository;
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
 * S-02 (interventoría): POST y PUT de movimientos validan el largo de cada campo contra el
 * esquema (`nombre` y `nombreEs` ≤ 100) y responden 400 con `campos`, nunca 500 desde la BD.
 * El cuerpo se recibe en un `record` de entrada, no en la entidad: lo que solo asigna el
 * servidor (`idMovimiento`, `esCompetitivo`) se ignora.
 */
@WebMvcTest(MovimientoController.class)
@ConSeguridad
@WithMockUser(roles = "ADMIN")
class MovimientoValidacionTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MovimientoRepository movimientoRepository;

    private static String json(String nombre, String nombreEs) {
        return "{\"nombre\": \"" + nombre + "\", \"nombreEs\": " + (nombreEs == null ? "null" : "\"" + nombreEs + "\"")
                + ", \"categoria\": \"Especial\", \"potencia\": 90}";
    }

    private ResultActions crear(String cuerpo) throws Exception {
        return mvc.perform(post("/api/movimientos").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions editar(String cuerpo) throws Exception {
        return mvc.perform(put("/api/movimientos/9").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void existeElMovimiento9() {
        Movimiento m = new Movimiento();
        m.setIdMovimiento(9);
        m.setNombre("thunder-punch");
        m.setCategoria("Físico");
        m.setPotencia(75);
        when(movimientoRepository.findById(9)).thenReturn(Optional.of(m));
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void postConUnNombreDe500CaracteresResponde400ConElCampoNombre() throws Exception {
        crear(json("a".repeat(500), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.campos.nombre").exists());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void postConUnNombreEnEspanolDeMasDe100CaracteresResponde400ConElCampoNombreEs() throws Exception {
        crear(json("rayo-prueba", "b".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombreEs").exists());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void putConUnNombreDe101CaracteresResponde400ConElCampoNombre() throws Exception {
        existeElMovimiento9();

        editar(json("a".repeat(101), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void putConUnNombreEnEspanolDeMasDe100CaracteresResponde400ConElCampoNombreEs() throws Exception {
        existeElMovimiento9();

        editar(json("rayo-prueba", "b".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombreEs").exists());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void conExactamente100CaracteresEnCadaCampoSiSeGuarda() throws Exception {
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(i -> i.getArgument(0));

        crear(json("a".repeat(100), "b".repeat(100))).andExpect(status().isCreated());
    }

    @Test
    void elCuerpoNoPuedeFijarElIdNiEsCompetitivo() throws Exception {
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(i -> i.getArgument(0));

        crear("""
                {"idMovimiento": 77, "esCompetitivo": true, "nombre": "golpe-suave",
                 "categoria": "Físico", "potencia": 10}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esCompetitivo").value(false));

        ArgumentCaptor<Movimiento> guardado = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(guardado.capture());
        assertThat(guardado.getValue().getIdMovimiento()).isNull();
        assertThat(guardado.getValue().getEsCompetitivo()).isFalse();
    }
}
