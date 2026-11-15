package com.pokedex.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Avance de la importación en segundo plano (F10). La escribe el hilo que importa y la leen
 * las peticiones de GET /api/pokemons/importacion, por eso todos los métodos son synchronized.
 * Vive en memoria: al reiniciar el servidor vuelve a "sin-iniciar".
 */
public class ProgresoImportacion {

    /** Lo que se devuelve como JSON en GET /api/pokemons/importacion. */
    public record Estado(String estado, String fase, int procesados, int total, int porcentaje,
                         int fallidos, LocalDateTime inicio, LocalDateTime fin, String mensaje,
                         List<PokemonService.ResultadoImportacion> resultados) {}

    private String estado = "sin-iniciar";
    private String fase;
    private int procesados;
    private int total;
    private int fallidos;
    private LocalDateTime inicio;
    private LocalDateTime fin;
    private String mensaje;
    private List<PokemonService.ResultadoImportacion> resultados;

    /** La importación siempre empieza por la fase de Pokémon. */
    public synchronized void iniciar(int totalPokemon) {
        estado = "en-curso";
        fase = "pokemons";
        procesados = 0;
        total = totalPokemon;
        fallidos = 0;
        inicio = LocalDateTime.now();
        fin = null;
        mensaje = null;
        resultados = null;
    }

    /** Empieza una fase (pokemons o movimientos) con su propio contador. */
    public synchronized void fase(String nombre, int totalDeLaFase) {
        fase = nombre;
        total = totalDeLaFase;
        procesados = 0;
    }

    public synchronized void avanzar(boolean fallido) {
        procesados++;
        if (fallido) {
            fallidos++;
        }
    }

    public synchronized void terminar(List<PokemonService.ResultadoImportacion> resultadosFinales) {
        estado = "terminada";
        fase = null;
        fin = LocalDateTime.now();
        resultados = List.copyOf(resultadosFinales);
    }

    /** Error inesperado que detuvo la importación. El detalle va al log, no aquí. */
    public synchronized void fallar() {
        estado = "error";
        fin = LocalDateTime.now();
        mensaje = "La importación se detuvo por un error inesperado. Revisa el log del servidor.";
    }

    public synchronized Estado instantanea() {
        int porcentaje = total > 0 ? procesados * 100 / total : 0;
        return new Estado(estado, fase, procesados, total, porcentaje, fallidos, inicio, fin, mensaje, resultados);
    }
}
