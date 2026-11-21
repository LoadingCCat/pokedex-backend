package com.pokedex.service;

import com.pokedex.exception.VisionNoDisponibleException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Visión simulada para las pruebas y para el perfil {@code e2e}: no llama a nadie ni gasta cuota.
 * Responde lo que se le configure; por defecto, Charmander con confianza 0,93.
 */
@Service
@Profile("e2e")
public class ServicioVisionMock implements ServicioVision {

    private volatile ResultadoVision resultado = new ResultadoVision(true, "charmander", 0.93);
    private volatile boolean fallar = false;
    private final AtomicInteger llamadas = new AtomicInteger();

    @Override
    public ResultadoVision identificar(byte[] imagen, String tipoImagen) {
        llamadas.incrementAndGet();
        if (fallar) {
            throw new VisionNoDisponibleException("El reconocimiento no está disponible en este momento");
        }
        return resultado;
    }

    /** Lo que responderá a partir de ahora (y deja de fallar). */
    public void responder(ResultadoVision nuevo) {
        this.resultado = nuevo;
        this.fallar = false;
    }

    /** Hace que cada llamada lance {@link VisionNoDisponibleException}, como un timeout. */
    public void fallar() {
        this.fallar = true;
    }

    /** Cuántas veces se llamó: sirve para comprobar que una validación corta antes de la IA. */
    public int llamadas() {
        return llamadas.get();
    }
}
