package com.pokedex.service;

import com.pokedex.exception.BadRequestException;
import com.pokedex.exception.CamposInvalidosException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Cuerpo de POST y PUT /api/pokemons (F15), ya validado. Se lee como Map, igual que el JSON de la
 * PokeAPI, para poder decir qué campo está mal aunque traiga texto donde va un número
 * ("ataque": "abc" → campos.ataque), cosa que con un objeto tipado sería un error de JSON genérico.
 *
 * Obligatorios: nombreEs, las 6 estadísticas (enteros de 1 a 255), altura y peso (≥ 0),
 * tipoObtencion (uno de los 9) y tipos (1 o 2). Al crear, además: idPokedex (> 0), nombre (slug)
 * y generacion (1–9). Opcionales: descripcion, formaObtencion, imagenUrl (https), rutas (ids).
 * En PUT, un opcional que no se envía se conserva (`enviados` dice cuáles llegaron).
 */
public record DatosPokemon(
        Integer idPokedex, String nombre, String nombreEs, Integer generacion,
        String descripcion, String tipoObtencion, String formaObtencion, String imagenUrl,
        Double altura, Double peso,
        Integer ps, Integer ataque, Integer defensa, Integer ataqueEspecial, Integer defensaEspecial, Integer velocidad,
        List<String> tipos, List<Integer> rutas,
        Set<String> enviados) {

    static final List<String> ESTADISTICAS =
            List.of("ps", "ataque", "defensa", "ataqueEspecial", "defensaEspecial", "velocidad");
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_ESTADISTICA = 255;
    private static final int MAX_GENERACION = 9;

    /** Lee y valida el cuerpo; si algo falla, lanza CamposInvalidosException con todos los errores. */
    public static DatosPokemon desde(Map<String, Object> cuerpo, boolean creando) {
        if (cuerpo == null) {
            throw new BadRequestException("Falta el cuerpo de la petición");
        }
        Lector l = new Lector(cuerpo);

        Integer idPokedex = creando ? l.entero("idPokedex", 1, Integer.MAX_VALUE, true) : null;
        String nombre = creando ? l.slug("nombre") : null;
        Integer generacion = l.entero("generacion", 1, MAX_GENERACION, creando);
        String nombreEs = l.texto("nombreEs", true, 100);
        String descripcion = l.texto("descripcion", false, 2000);
        String tipoObtencion = l.obtencion("tipoObtencion");
        String formaObtencion = l.texto("formaObtencion", false, 2000);
        String imagenUrl = l.url("imagenUrl");
        Double altura = l.decimal("altura");
        Double peso = l.decimal("peso");
        Integer[] stats = new Integer[ESTADISTICAS.size()];
        for (int i = 0; i < stats.length; i++) {
            stats[i] = l.entero(ESTADISTICAS.get(i), 1, MAX_ESTADISTICA, true);
        }
        List<String> tipos = l.tipos("tipos");
        List<Integer> rutas = l.ids("rutas");

        if (!l.errores.isEmpty()) {
            throw new CamposInvalidosException(l.errores);
        }
        return new DatosPokemon(idPokedex, nombre, nombreEs, generacion, descripcion, tipoObtencion,
                formaObtencion, imagenUrl, altura, peso,
                stats[0], stats[1], stats[2], stats[3], stats[4], stats[5],
                tipos, rutas, Set.copyOf(cuerpo.keySet()));
    }

    public boolean enviado(String campo) {
        return enviados.contains(campo);
    }

    /** Recorre el cuerpo y junta un mensaje por cada campo inválido. */
    private static final class Lector {
        private final Map<String, Object> cuerpo;
        final Map<String, String> errores = new LinkedHashMap<>();

        Lector(Map<String, Object> cuerpo) {
            this.cuerpo = cuerpo;
        }

        private boolean vacio(Object valor) {
            return valor == null || (valor instanceof String s && s.isBlank());
        }

        Integer entero(String campo, int min, int max, boolean obligatorio) {
            Object valor = cuerpo.get(campo);
            if (vacio(valor)) {
                if (obligatorio) {
                    errores.put(campo, "Es obligatorio");
                }
                return null;
            }
            Long numero = null;
            if (valor instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
                numero = n.longValue();
            } else if (valor instanceof String s && s.trim().matches("-?\\d{1,10}")) {
                numero = Long.parseLong(s.trim());
            }
            if (numero == null) {
                errores.put(campo, "Debe ser un número entero");
                return null;
            }
            if (numero < min || numero > max) {
                errores.put(campo, max == Integer.MAX_VALUE
                        ? "Debe ser mayor o igual a " + min
                        : "Debe estar entre " + min + " y " + max);
                return null;
            }
            return numero.intValue();
        }

        Double decimal(String campo) {
            Object valor = cuerpo.get(campo);
            if (vacio(valor)) {
                errores.put(campo, "Es obligatorio");
                return null;
            }
            Double numero = null;
            if (valor instanceof Number n) {
                numero = n.doubleValue();
            } else if (valor instanceof String s) {
                try {
                    numero = Double.parseDouble(s.trim().replace(',', '.'));
                } catch (NumberFormatException e) {
                    numero = null;
                }
            }
            if (numero == null || numero.isNaN() || numero.isInfinite()) {
                errores.put(campo, "Debe ser un número");
                return null;
            }
            if (numero < 0) {
                errores.put(campo, "No puede ser negativo");
                return null;
            }
            return numero;
        }

        String texto(String campo, boolean obligatorio, int maximo) {
            Object valor = cuerpo.get(campo);
            if (vacio(valor)) {
                if (obligatorio) {
                    errores.put(campo, "Es obligatorio");
                }
                return null;
            }
            if (!(valor instanceof String s)) {
                errores.put(campo, "Debe ser un texto");
                return null;
            }
            String limpio = s.trim();
            if (limpio.length() > maximo) {
                errores.put(campo, "Debe tener como mucho " + maximo + " caracteres");
                return null;
            }
            return limpio;
        }

        String slug(String campo) {
            String texto = texto(campo, true, 100);
            if (texto == null) {
                return null;
            }
            String slug = texto.toLowerCase();
            if (!SLUG.matcher(slug).matches()) {
                errores.put(campo, "Usa minúsculas, números y guiones (por ejemplo, mi-pokemon)");
                return null;
            }
            return slug;
        }

        String url(String campo) {
            String texto = texto(campo, false, 500);
            if (texto != null && !texto.startsWith("https://")) {
                errores.put(campo, "Debe ser una dirección https://");
                return null;
            }
            return texto;
        }

        String obtencion(String campo) {
            String texto = texto(campo, true, 30);
            if (texto == null) {
                return null;
            }
            String oficial = FormasObtencion.oficial(texto);
            if (oficial == null) {
                errores.put(campo, "Debe ser uno de: " + String.join(", ", FormasObtencion.VALORES));
            }
            return oficial;
        }

        /** 1 o 2 nombres de tipo distintos, en minúsculas. Que existan lo comprueba el servicio. */
        List<String> tipos(String campo) {
            Object valor = cuerpo.get(campo);
            if (!(valor instanceof List<?> lista) || lista.isEmpty()) {
                errores.put(campo, "Elige 1 o 2 tipos");
                return null;
            }
            List<String> tipos = new ArrayList<>();
            for (Object t : lista) {
                if (!(t instanceof String s) || s.isBlank()) {
                    errores.put(campo, "Cada tipo debe ser un nombre, como fire o water");
                    return null;
                }
                String nombre = s.trim().toLowerCase();
                if (!tipos.contains(nombre)) {
                    tipos.add(nombre);
                }
            }
            if (tipos.size() > 2) {
                errores.put(campo, "Un Pokémon tiene como mucho 2 tipos");
                return null;
            }
            return tipos;
        }

        /** Lista de ids enteros (opcional). Que existan lo comprueba el servicio. */
        List<Integer> ids(String campo) {
            Object valor = cuerpo.get(campo);
            if (valor == null) {
                return null;
            }
            if (!(valor instanceof List<?> lista)) {
                errores.put(campo, "Debe ser una lista de ids de rutas");
                return null;
            }
            List<Integer> ids = new ArrayList<>();
            for (Object id : lista) {
                if (!(id instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
                    errores.put(campo, "Debe ser una lista de ids de rutas");
                    return null;
                }
                if (!ids.contains(n.intValue())) {
                    ids.add(n.intValue());
                }
            }
            return ids;
        }
    }
}
