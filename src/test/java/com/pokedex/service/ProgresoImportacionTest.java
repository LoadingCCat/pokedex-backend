package com.pokedex.service;

import com.pokedex.service.PokemonService.ResultadoImportacion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Criterios de F10 sobre el estado que devuelve GET /api/pokemons/importacion. */
class ProgresoImportacionTest {

    @Test
    void antesDeImportarEstaSinIniciar() {
        ProgresoImportacion.Estado estado = new ProgresoImportacion().instantanea();

        assertThat(estado.estado()).isEqualTo("sin-iniciar");
        assertThat(estado.inicio()).isNull();
        assertThat(estado.resultados()).isNull();
    }

    @Test
    void cuentaProcesadosFallidosYPorcentajeDeLaFase() {
        ProgresoImportacion progreso = new ProgresoImportacion();
        progreso.iniciar(4);
        progreso.avanzar(false);
        progreso.avanzar(true);

        ProgresoImportacion.Estado estado = progreso.instantanea();
        assertThat(estado.estado()).isEqualTo("en-curso");
        assertThat(estado.fase()).isEqualTo("pokemons");
        assertThat(estado.procesados()).isEqualTo(2);
        assertThat(estado.total()).isEqualTo(4);
        assertThat(estado.porcentaje()).isEqualTo(50);
        assertThat(estado.fallidos()).isEqualTo(1);
    }

    @Test
    void unaFaseNuevaReiniciaElContadorPeroNoLosFallidos() {
        ProgresoImportacion progreso = new ProgresoImportacion();
        progreso.iniciar(1);
        progreso.avanzar(true);
        progreso.fase("movimientos", 10);

        ProgresoImportacion.Estado estado = progreso.instantanea();
        assertThat(estado.fase()).isEqualTo("movimientos");
        assertThat(estado.procesados()).isZero();
        assertThat(estado.fallidos()).isEqualTo(1);
    }

    @Test
    void alTerminarGuardaResultadosYFin() {
        ProgresoImportacion progreso = new ProgresoImportacion();
        progreso.iniciar(1);
        progreso.terminar(List.of(new ResultadoImportacion("pokemons", 1, 0, 0)));

        ProgresoImportacion.Estado estado = progreso.instantanea();
        assertThat(estado.estado()).isEqualTo("terminada");
        assertThat(estado.fase()).isNull();
        assertThat(estado.fin()).isNotNull();
        assertThat(estado.resultados()).hasSize(1);
    }

    @Test
    void unErrorDejaUnMensajeGenerico() {
        ProgresoImportacion progreso = new ProgresoImportacion();
        progreso.iniciar(1);
        progreso.fallar();

        assertThat(progreso.instantanea().estado()).isEqualTo("error");
        assertThat(progreso.instantanea().mensaje()).contains("log del servidor");
    }

    @Test
    void reiniciarBorraLosResultadosAnteriores() {
        ProgresoImportacion progreso = new ProgresoImportacion();
        progreso.iniciar(1);
        progreso.terminar(List.of(new ResultadoImportacion("pokemons", 1, 0, 0)));
        progreso.iniciar(5);

        assertThat(progreso.instantanea().resultados()).isNull();
        assertThat(progreso.instantanea().fin()).isNull();
    }
}
