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
 * S-01: como máximo 5 intentos de login fallidos por minuto por IP, en memoria y con ventana
 * deslizante, igual que {@link LimiteIdentificaciones}. Solo cuentan los fallos: un login
 * correcto no consume cupo. {@link #verificar} se llama antes de comprobar la contraseña (así un
 * atacante bloqueado no gasta BCrypt) y {@link #registrarFallo} después de cada fallo.
 *
 * <p>No crece sin límite: cada IP guarda a lo sumo 5 marcas y las IP sin actividad en el último
 * minuto se borran como mucho una vez por minuto. Es por instancia (Render gratuito corre una).
 */
@Component
public class LimiteLogin {

    public static final int MAXIMO_FALLOS_POR_VENTANA = 5;
    public static final long VENTANA_MS = 60_000L;
    static final String MENSAJE = "Demasiados intentos de inicio de sesión. Espera un minuto";

    private final Clock reloj;
    private final Map<String, ArrayDeque<Long>> fallosPorIp = new HashMap<>();
    private long ultimaLimpieza;

    @Autowired
    public LimiteLogin() {
        this(Clock.systemUTC());
    }

    /** Con reloj inyectable, para probar sin esperar un minuto real. */
    LimiteLogin(Clock reloj) {
        this.reloj = reloj;
        this.ultimaLimpieza = reloj.millis();
    }

    /** @throws LimiteExcedidoException si la IP ya falló 5 veces en el último minuto */
    public synchronized void verificar(String ip) {
        long ahora = reloj.millis();
        limpiarSiToca(ahora);
        ArrayDeque<Long> marcas = fallosPorIp.get(clave(ip));
        if (marcas == null) {
            return;
        }
        expirar(marcas, ahora);
        if (marcas.size() >= MAXIMO_FALLOS_POR_VENTANA) {
            throw new LimiteExcedidoException(MENSAJE);
        }
    }

    /** Anota un intento fallido de esa IP. */
    public synchronized void registrarFallo(String ip) {
        long ahora = reloj.millis();
        limpiarSiToca(ahora);
        ArrayDeque<Long> marcas = fallosPorIp.computeIfAbsent(clave(ip), k -> new ArrayDeque<>());
        expirar(marcas, ahora);
        if (marcas.size() < MAXIMO_FALLOS_POR_VENTANA) {
            marcas.addLast(ahora);
        }
    }

    /** Cuántas IP tiene en memoria ahora (para probar que se limpian). */
    synchronized int ipsEnMemoria() {
        return fallosPorIp.size();
    }

    private static String clave(String ip) {
        return ip == null || ip.isBlank() ? "desconocida" : ip;
    }

    private static void expirar(ArrayDeque<Long> marcas, long ahora) {
        while (!marcas.isEmpty() && marcas.peekFirst() <= ahora - VENTANA_MS) {
            marcas.pollFirst();
        }
    }

    private void limpiarSiToca(long ahora) {
        if (ahora - ultimaLimpieza < VENTANA_MS) {
            return;
        }
        ultimaLimpieza = ahora;
        Iterator<ArrayDeque<Long>> it = fallosPorIp.values().iterator();
        while (it.hasNext()) {
            ArrayDeque<Long> marcas = it.next();
            if (marcas.isEmpty() || marcas.peekLast() <= ahora - VENTANA_MS) {
                it.remove();
            }
        }
    }
}
