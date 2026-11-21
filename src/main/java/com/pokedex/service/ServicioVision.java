package com.pokedex.service;

import com.pokedex.exception.VisionNoDisponibleException;

/** Servicio externo que mira una foto y dice qué Pokémon es (F20). */
public interface ServicioVision {

    /**
     * @param imagen    bytes de la foto, ya validada por el controller
     * @param tipoImagen tipo real de la imagen: {@code image/jpeg}, {@code image/png} o {@code image/webp}
     * @throws VisionNoDisponibleException si el servicio no responde, falla, responde algo
     *                                     inesperado o no está configurado
     */
    ResultadoVision identificar(byte[] imagen, String tipoImagen);
}
