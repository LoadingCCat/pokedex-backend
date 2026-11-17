package com.pokedex.controller;

import com.pokedex.entity.Movimiento;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.ConSeguridad;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de F4 y F6 en el controller de movimientos (sin PostgreSQL). */
@WebMvcTest(MovimientoController.class)
@ConSeguridad
// Las pruebas de escritura de F1–F14 corren como ADMIN; las de 401/403 están en SeguridadControllerTest (F15)
@WithMockUser(roles = "ADMIN")
class MovimientoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MovimientoRepository movimientoRepository;

    private static Movimiento truenoPuno() {
        Movimiento m = new Movimiento();
        m.setIdMovimiento(9);
        m.setNombre("thunder-punch");
        m.setNombreEs("Puño Trueno");
        m.setTipo("electric");
        m.setCategoria("Físico");
        m.setPotencia(75);
        m.setEsCompetitivo(false);
        return m;
    }

    private void devolverLoQueSeGuarda() {
        when(movimientoRepository.save(any(Movimiento.class))).thenAnswer(i -> i.getArgument(0));
    }

    private org.springframework.test.web.servlet.ResultActions crear(String json) throws Exception {
        return mvc.perform(post("/api/movimientos").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void listadoPaginadoOrdenadoPorNombre() throws Exception {
        when(movimientoRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(truenoPuno())));

        mvc.perform(get("/api/movimientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombreEs").value("Puño Trueno"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(movimientoRepository).findAll(pagina.capture());
        assertThat(pagina.getValue().getPageSize()).isEqualTo(20);
        assertThat(pagina.getValue().getSort()).isEqualTo(Sort.by("nombre"));
    }

    @Test
    void movimientoInexistenteResponde404() throws Exception {
        when(movimientoRepository.findById(9999)).thenReturn(Optional.empty());

        mvc.perform(get("/api/movimientos/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Movimiento con id 9999 no existe"));
    }

    @Test
    void busquedaConNombreVacioResponde400() throws Exception {
        mvc.perform(get("/api/movimientos/buscar?nombre=")).andExpect(status().isBadRequest());
        verify(movimientoRepository, never()).buscarPorNombre(any(), any());
    }

    @Test
    void crearCalculaEsCompetitivoYNormalizaLaCategoria() throws Exception {
        devolverLoQueSeGuarda();

        crear("{\"nombre\":\" mega-golpe \",\"categoria\":\"físico\",\"potencia\":90,\"esCompetitivo\":false}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("mega-golpe"))
                .andExpect(jsonPath("$.categoria").value("Físico"))
                .andExpect(jsonPath("$.esCompetitivo").value(true));
    }

    @Test
    void crearSinPotenciaLaDejaEnCeroYNoEsCompetitivo() throws Exception {
        devolverLoQueSeGuarda();

        crear("{\"nombre\":\"gruñido\",\"categoria\":\"Estado\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.potencia").value(0))
                .andExpect(jsonPath("$.esCompetitivo").value(false));
    }

    @Test
    void crearConNombreEsEnBlancoLoGuardaComoNull() throws Exception {
        devolverLoQueSeGuarda();

        crear("{\"nombre\":\"x\",\"nombreEs\":\"   \",\"categoria\":\"Especial\",\"potencia\":10}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreEs").doesNotExist());
    }

    @Test
    void crearConTipoLoGuardaEnMinusculas() throws Exception {
        devolverLoQueSeGuarda();

        crear("{\"nombre\":\"x\",\"tipo\":\" FIRE \",\"categoria\":\"Especial\",\"potencia\":10}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("fire"));
    }

    @Test
    void crearConUnTipoQueNoExisteResponde400() throws Exception {
        crear("{\"nombre\":\"x\",\"tipo\":\"luz\",\"categoria\":\"Especial\",\"potencia\":10}")
                .andExpect(status().isBadRequest());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void crearIgnoraElIdEnviado() throws Exception {
        devolverLoQueSeGuarda();

        crear("{\"idMovimiento\":9,\"nombre\":\"x\",\"categoria\":\"Especial\",\"potencia\":10}")
                .andExpect(status().isCreated());

        ArgumentCaptor<Movimiento> guardado = ArgumentCaptor.forClass(Movimiento.class);
        verify(movimientoRepository).save(guardado.capture());
        assertThat(guardado.getValue().getIdMovimiento()).isNull();
    }

    @Test
    void crearConDatosInvalidosResponde400() throws Exception {
        crear("{\"categoria\":\"Físico\",\"potencia\":10}").andExpect(status().isBadRequest());
        crear("{\"nombre\":\"x\",\"categoria\":\"Fuego\",\"potencia\":10}").andExpect(status().isBadRequest());
        crear("{\"nombre\":\"x\",\"categoria\":\"Físico\",\"potencia\":-5}").andExpect(status().isBadRequest());
        crear("{ esto no es json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición no es un JSON válido"));
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void crearUnNombreRepetidoResponde400() throws Exception {
        when(movimientoRepository.existsByNombreIgnoreCase("thunder-punch")).thenReturn(true);

        crear("{\"nombre\":\"thunder-punch\",\"categoria\":\"Físico\",\"potencia\":75}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizarSinNombreEsNiTipoConservaLosAnteriores() throws Exception {
        when(movimientoRepository.findById(9)).thenReturn(Optional.of(truenoPuno()));
        devolverLoQueSeGuarda();

        mvc.perform(put("/api/movimientos/9").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"thunder-punch\",\"categoria\":\"Físico\",\"potencia\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreEs").value("Puño Trueno"))
                .andExpect(jsonPath("$.tipo").value("electric"))
                .andExpect(jsonPath("$.potencia").value(100))
                .andExpect(jsonPath("$.esCompetitivo").value(true));
    }

    @Test
    void eliminarDesvinculaDeLosPokemonAntesDeBorrar() throws Exception {
        Movimiento movimiento = truenoPuno();
        when(movimientoRepository.findById(9)).thenReturn(Optional.of(movimiento));

        mvc.perform(delete("/api/movimientos/9")).andExpect(status().isNoContent());

        verify(movimientoRepository).desvincularDePokemons(eq(9));
        verify(movimientoRepository).delete(movimiento);
    }
}
