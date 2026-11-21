package com.pokedex.service;

import com.pokedex.exception.LimiteExcedidoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * F20: como máximo 10 identificaciones por minuto por IP, contadas en memoria con una ventana
 * deslizante (se guardan las marcas de tiempo de los últimos 60 s de cada IP).
 *
 * <p>No crece sin límite: cada IP guarda a lo sumo 10 marcas, y las IP sin actividad en el
 * último minuto se borran como mucho una vez por minuto. Los intentos rechazados no se
 * cuentan. Es por instancia: con varias réplicas cada una lleva su propia cuenta (Render
 * gratuito corre una sola).
 */
@Component
public class LimiteIdentificaciones {

    public static final int MAXIMO_POR_VENTANA = 10;
    public static final long VENTANA_MS = 60_000L;

    private final Clock reloj;
    private final Map<String, ArrayDeque<Long>> marcasPorIp = new HashMap<>();
    private long ultimaLimpieza;

    @Autowired
    public LimiteIdentificaciones() {
        this(Clock.systemUTC());
    }

    /** Con reloj inyectable, para probar sin esperar un minuto real. */
    LimiteIdentificaciones(Clock reloj) {
        this.reloj = reloj;
        this.ultimaLimpieza = reloj.millis();
    }

    /**
     * Anota una identificación de esa IP.
     *
     * @throws LimiteExcedidoException si ya hizo 10 en el último minuto (esta no se anota)
     */
    public synchronized void registrar(String ip) {
        long ahora = reloj.millis();
        limpiarSiToca(ahora);

        ArrayDeque<Long> marcas = marcasPorIp.computeIfAbsent(
                ip == null || ip.isBlank() ? "desconocida" : ip, k -> new ArrayDeque<>());
        while (!marcas.isEmpty() && marcas.peekFirst() <= ahora - VENTANA_MS) {
            marcas.pollFirst();
        }
        if (marcas.size() >= MAXIMO_POR_VENTANA) {
            throw new LimiteExcedidoException();
        }
        marcas.addLast(ahora);
    }

    /** Cuántas IP tiene en memoria ahora (para probar que se limpian). */
    synchronized int ipsEnMemoria() {
        return marcasPorIp.size();
    }

    private void limpiarSiToca(long ahora) {
        if (ahora - ultimaLimpieza < VENTANA_MS) {
            return;
        }
        ultimaLimpieza = ahora;
        Iterator<ArrayDeque<Long>> it = marcasPorIp.values().iterator();
        while (it.hasNext()) {
            ArrayDeque<Long> marcas = it.next();
            if (marcas.isEmpty() || marcas.peekLast() <= ahora - VENTANA_MS) {
                it.remove();
            }
        }
    }
}
