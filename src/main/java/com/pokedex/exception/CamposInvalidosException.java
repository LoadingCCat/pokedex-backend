package com.pokedex.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 400 con el detalle de cada campo inválido (F15, RF-12): el JSON de error suma
 * "campos": { "ataque": "Debe estar entre 1 y 255", ... } para que el panel muestre cada
 * mensaje junto a su campo.
 */
public class CamposInvalidosException extends BadRequestException {

    private final Map<String, String> campos;

    public CamposInvalidosException(Map<String, String> campos) {
        this("Hay datos inválidos en: " + String.join(", ", campos.keySet()), campos);
    }

    /** Con un mensaje propio (los 400 de movimientos y rutas conservan el que tenían antes de F15). */
    public CamposInvalidosException(String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.campos = Collections.unmodifiableMap(new LinkedHashMap<>(campos));
    }

    /** Un solo campo, con el mismo texto como mensaje. */
    public static CamposInvalidosException de(String campo, String mensaje) {
        return new CamposInvalidosException(mensaje, Map.of(campo, mensaje));
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
