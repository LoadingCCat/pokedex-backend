package com.pokedex.service;

import com.pokedex.entity.Movimiento;
import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Ruta;
import com.pokedex.entity.Tipo;
import com.pokedex.exception.ConflictException;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.repository.TipoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Importa datos desde la PokeAPI (https://pokeapi.co/api/v2/) hacia PostgreSQL.
 *
 * Por cada Pokémon se consultan:
 *  - /pokemon/{id}            -> nombre, tipos, altura, peso, imagen
 *  - /pokemon-species/{id}    -> generación real y descripción (en español si existe)
 *  - /pokemon/{id}/encounters -> lugares REALES de los juegos donde aparece en estado salvaje
 *
 * Cada área de encuentro pertenece a un lugar (/location/{id}) y cada lugar a una región.
 * Los lugares se guardan como Ruta (una sola vez cada uno, identificados por idPokeapi).
 */
@Service
public class PokemonService {

    private static final Logger log = LoggerFactory.getLogger(PokemonService.class);
    private static final String POKEAPI = "https://pokeapi.co/api/v2";

    /** Último número de la Pokédex Nacional (generación 9). */
    public static final int MAX_POKEMON = 1025;

    /** Región de origen de cada generación (índice 0 = generación 1). */
    private static final String[] REGION_POR_GENERACION = {
        "Kanto", "Johto", "Hoenn", "Sinnoh", "Teselia", "Kalos", "Alola", "Galar", "Paldea"
    };

    /** Último número de Pokédex de cada generación (respaldo si species falla). */
    private static final int[] ULTIMO_ID_POR_GENERACION = {151, 251, 386, 493, 649, 721, 809, 905, 1025};

    /** Nombres en español de las regiones que la PokeAPI entrega en inglés. */
    private static final Map<String, String> REGION_EN_ESPANOL = Map.of(
        "unova", "Teselia"
    );

    /**
     * Iniciales que se eligen al comenzar cada juego principal (la PokeAPI no los marca).
     * Incluye a Pikachu (Pokémon Amarillo) y Eevee (Let's Go Eevee).
     */
    private static final Set<Integer> INICIALES = Set.of(
        1, 4, 7, 25, 133,          // Kanto (+ Pikachu y Eevee)
        152, 155, 158,             // Johto
        252, 255, 258,             // Hoenn
        387, 390, 393,             // Sinnoh
        495, 498, 501,             // Teselia
        650, 653, 656,             // Kalos
        722, 725, 728,             // Alola
        810, 813, 816,             // Galar
        906, 909, 912              // Paldea
    );

    /** Pokémon que se reviven a partir de un fósil (la PokeAPI no los marca). */
    private static final Set<Integer> FOSILES = Set.of(
        138, 139, 140, 141, 142,   // Omanyte, Omastar, Kabuto, Kabutops, Aerodactyl
        345, 346, 347, 348,        // Lileep, Cradily, Anorith, Armaldo
        408, 409, 410, 411,        // Cranidos, Rampardos, Shieldon, Bastiodon
        564, 565, 566, 567,        // Tirtouga, Carracosta, Archen, Archeops
        696, 697, 698, 699,        // Tyrunt, Tyrantrum, Amaura, Aurorus
        880, 881, 882, 883         // Dracozolt, Arctozolt, Dracovish, Arctovish
    );

    /** Traducción de los métodos de encuentro de la PokeAPI. */
    private static final Map<String, String> METODOS_EN_ESPANOL = Map.ofEntries(
        Map.entry("walk", "hierba alta y cuevas"),
        Map.entry("dark-grass", "hierba alta oscura"),
        Map.entry("grass-spots", "hierba que se mueve"),
        Map.entry("cave-spots", "polvo en cuevas"),
        Map.entry("bridge-spots", "sombras en puentes"),
        Map.entry("old-rod", "caña vieja"),
        Map.entry("good-rod", "caña buena"),
        Map.entry("super-rod", "supercaña"),
        Map.entry("super-rod-spots", "supercaña en burbujas"),
        Map.entry("surf", "surf"),
        Map.entry("surf-spots", "surf en burbujas"),
        Map.entry("rock-smash", "golpe roca"),
        Map.entry("headbutt", "golpe cabeza a árboles"),
        Map.entry("rough-terrain", "terreno abrupto"),
        Map.entry("yellow-flowers", "flores amarillas"),
        Map.entry("purple-flowers", "flores moradas"),
        Map.entry("red-flowers", "flores rojas"),
        Map.entry("gift", "regalo de un personaje"),
        Map.entry("gift-egg", "huevo de regalo"),
        Map.entry("only-one", "encuentro único"),
        Map.entry("pokeflute", "despertándolo con la Poké Flauta"),
        Map.entry("squirt-bottle", "regadera"),
        Map.entry("wailmer-pail", "regadera Wailmer"),
        Map.entry("seaweed", "algas marinas")
    );

    /** Lo que se obtiene al consultar los encuentros de un Pokémon. */
    private record Encuentros(List<Ruta> rutas, Set<String> metodos) {}

    /** Resultado de una importación: cuántos registros entraron, cuántos ya existían y cuántos fallaron. */
    public record ResultadoImportacion(String recurso, int importados, int omitidos, int fallidos) {}

    /**
     * Memoria temporal durante una importación para no pedir dos veces lo mismo a la
     * PokeAPI: muchos Pokémon comparten las mismas áreas y los mismos movimientos.
     */
    private static class CacheImportacion {
        // Rutas
        final Map<String, Ruta> porArea = new HashMap<>();      // nombre del área -> Ruta
        final Map<String, Ruta> porLugar = new HashMap<>();     // URL del lugar   -> Ruta
        int creadas = 0;
        int existentes = 0;
        int fallidas = 0;

        // Movimientos que aprenden los Pokémon
        final Map<String, Movimiento> movimientosPorNombre = new HashMap<>(); // nombre -> Movimiento
        final Set<String> movimientosFallidos = new HashSet<>();               // no se reintentan
        int movimientosCreados = 0;
        int movimientosExistentes = 0;
    }

    private final PokemonRepository pokemonRepository;
    private final TipoRepository tipoRepository;
    private final MovimientoRepository movimientoRepository;
    private final RutaRepository rutaRepository;
    private final RestTemplate restTemplate;

    public PokemonService(PokemonRepository pokemonRepository, TipoRepository tipoRepository,
                          MovimientoRepository movimientoRepository, RutaRepository rutaRepository,
                          RestTemplate restTemplate) {
        this.pokemonRepository = pokemonRepository;
        this.tipoRepository = tipoRepository;
        this.movimientoRepository = movimientoRepository;
        this.rutaRepository = rutaRepository;
        this.restTemplate = restTemplate;
    }

    // =====================================================================
    // IMPORTACIÓN COMPLETA
    // =====================================================================

    /** true mientras hay una importación corriendo (evita dos importaciones al mismo tiempo). */
    private final AtomicBoolean importacionEnCurso = new AtomicBoolean(false);

    /** Importa Pokémon (con sus rutas reales) y movimientos. */
    public List<ResultadoImportacion> importarTodo(int totalPokemon, int totalMovimientos) {
        if (!importacionEnCurso.compareAndSet(false, true)) {
            throw new ConflictException("Ya hay una importación en curso. Espera a que termine.");
        }
        try {
            limpiarRutasDeEjemplo();

            CacheImportacion cache = new CacheImportacion();
            List<ResultadoImportacion> resultados = new ArrayList<>();
            resultados.add(importarPokemonsMasivo(totalPokemon, cache));
            resultados.add(new ResultadoImportacion("rutas", cache.creadas, cache.existentes, cache.fallidas));
            resultados.add(new ResultadoImportacion("movimientos-aprendidos", cache.movimientosCreados,
                    cache.movimientosExistentes, cache.movimientosFallidos.size()));
            resultados.add(importarMovimientosMasivo(totalMovimientos));
            return resultados;
        } finally {
            importacionEnCurso.set(false);
        }
    }

    /** Borra las rutas inventadas que creaba la versión anterior del importador. */
    private void limpiarRutasDeEjemplo() {
        rutaRepository.desvincularRutasDeEjemplo();
        int borradas = rutaRepository.borrarRutasDeEjemplo();
        if (borradas > 0) {
            log.info("Se borraron {} rutas de ejemplo de la versión anterior", borradas);
        }
    }

    // =====================================================================
    // POKÉMON
    // =====================================================================

    public ResultadoImportacion importarPokemonsMasivo(int total) {
        return importarPokemonsMasivo(total, new CacheImportacion());
    }

    private ResultadoImportacion importarPokemonsMasivo(int total, CacheImportacion cache) {
        int importados = 0;
        int fallidos = 0;

        for (int id = 1; id <= total; id++) {
            try {
                importarPokemon(id, cache);
                importados++;
            } catch (Exception e) {
                fallidos++;
                log.warn("Error al importar el Pokémon ID {}: {}", id, e.getMessage());
            }
            if (id % 25 == 0) {
                log.info("Importando Pokémon... {}/{}", id, total);
            }
        }
        log.info("Pokémon importados: {} (fallidos: {})", importados, fallidos);
        return new ResultadoImportacion("pokemons", importados, 0, fallidos);
    }

    private void importarPokemon(int id, CacheImportacion cache) {
        Map<String, Object> datos = getJson(POKEAPI + "/pokemon/" + id);
        if (datos == null) {
            throw new IllegalStateException("La PokeAPI no devolvió datos");
        }

        // species puede fallar sin que perdamos el Pokémon: usamos valores de respaldo
        Map<String, Object> especie = null;
        try {
            especie = getJson(POKEAPI + "/pokemon-species/" + id);
        } catch (Exception e) {
            log.warn("No se pudo obtener la especie del Pokémon ID {}: {}", id, e.getMessage());
        }

        Integer idPokedex = ((Number) datos.get("id")).intValue();
        int generacion = obtenerGeneracion(especie, idPokedex);

        Pokemon pokemon = pokemonRepository.findById(idPokedex).orElseGet(Pokemon::new);
        pokemon.setIdPokedex(idPokedex);
        pokemon.setNombre((String) datos.get("name"));
        // Si la especie falló o no trae nombres, se conserva el nombreEs que ya tenía
        String nombreEs = especie != null ? nombreEnEspanol(especie.get("names")) : null;
        if (nombreEs != null) {
            pokemon.setNombreEs(nombreEs);
        }
        pokemon.setDescripcion(obtenerDescripcion(especie));
        pokemon.setGeneracion(generacion);
        pokemon.setRegion(REGION_POR_GENERACION[generacion - 1]);
        pokemon.setImagenUrl(obtenerImagen(datos));
        pokemon.setAltura(decimal(datos.get("height"))); // PokeAPI: decímetros -> metros
        pokemon.setPeso(decimal(datos.get("weight")));   // PokeAPI: hectogramos -> kg
        pokemon.setTipos(obtenerTipos(datos));
        asignarEstadisticas(pokemon, datos);

        // Si los movimientos fallan, se conservan los que ya tenía (no se toca la lista)
        try {
            pokemon.setMovimientos(obtenerMovimientos(datos, cache));
        } catch (Exception e) {
            log.warn("No se pudieron obtener los movimientos del Pokémon ID {}: {}", id, e.getMessage());
        }

        // Si los encuentros fallan, se conservan las rutas que ya tenía
        Encuentros encuentros;
        try {
            encuentros = obtenerEncuentros(idPokedex, cache);
        } catch (Exception e) {
            log.warn("No se pudieron obtener las rutas del Pokémon ID {}: {}", id, e.getMessage());
            List<Ruta> anteriores = pokemon.getRutas() != null ? new ArrayList<>(pokemon.getRutas()) : new ArrayList<>();
            encuentros = new Encuentros(anteriores, Set.of());
        }
        pokemon.setRutas(encuentros.rutas());

        definirObtencion(pokemon, especie, encuentros);

        pokemonRepository.save(pokemon);
    }

    private List<Tipo> obtenerTipos(Map<String, Object> datos) {
        Set<Tipo> tipos = new LinkedHashSet<>();
        for (Map<String, Object> slot : lista(datos.get("types"))) {
            String nombreTipo = (String) mapa(slot.get("type")).get("name");
            Tipo tipo = tipoRepository.findByNombre(nombreTipo);
            if (tipo == null) {
                tipo = tipoRepository.save(new Tipo(nombreTipo));
            }
            tipos.add(tipo);
        }
        return new ArrayList<>(tipos);
    }

    /** Lee las 6 estadísticas base de "stats" (hp, attack, defense, special-attack, special-defense, speed). */
    private void asignarEstadisticas(Pokemon pokemon, Map<String, Object> datos) {
        for (Map<String, Object> entrada : lista(datos.get("stats"))) {
            String nombre = (String) mapa(entrada.get("stat")).get("name");
            Object valor = entrada.get("base_stat");
            if (nombre == null || !(valor instanceof Number numero)) {
                continue;
            }
            int base = numero.intValue();
            switch (nombre) {
                case "hp" -> pokemon.setPs(base);
                case "attack" -> pokemon.setAtaque(base);
                case "defense" -> pokemon.setDefensa(base);
                case "special-attack" -> pokemon.setAtaqueEspecial(base);
                case "special-defense" -> pokemon.setDefensaEspecial(base);
                case "speed" -> pokemon.setVelocidad(base);
                default -> { } // la PokeAPI no trae otras, pero se ignoran por si acaso
            }
        }
    }

    /**
     * Movimientos que puede aprender el Pokémon (en cualquier juego), de "moves" en /pokemon/{id}.
     * Los que no existen en la BD se descargan de /move/{id} una sola vez por importación.
     */
    private List<Movimiento> obtenerMovimientos(Map<String, Object> datos, CacheImportacion cache) {
        Set<Movimiento> movimientos = new LinkedHashSet<>();
        for (Map<String, Object> entrada : lista(datos.get("moves"))) {
            Map<String, Object> move = mapa(entrada.get("move"));
            String nombre = (String) move.get("name");
            String url = (String) move.get("url");
            if (nombre == null || url == null || cache.movimientosFallidos.contains(nombre)) {
                continue;
            }
            Movimiento movimiento = cache.movimientosPorNombre.get(nombre);
            if (movimiento == null) {
                movimiento = movimientoRepository.findFirstByNombreIgnoreCase(nombre).orElse(null);
                if (movimiento != null) {
                    cache.movimientosExistentes++;
                    // Ya existe: solo se refresca su nombre en español (una vez por importación)
                    try {
                        movimiento = actualizarNombreEs(movimiento, getJson(url));
                    } catch (Exception e) {
                        log.warn("No se pudo actualizar el nombre en español del movimiento {}: {}",
                                nombre, e.getMessage());
                    }
                } else {
                    try {
                        movimiento = movimientoRepository.save(crearMovimiento(getJson(url)));
                        cache.movimientosCreados++;
                    } catch (Exception e) {
                        cache.movimientosFallidos.add(nombre);
                        log.warn("No se pudo obtener el movimiento {}: {}", nombre, e.getMessage());
                        continue;
                    }
                }
                cache.movimientosPorNombre.put(nombre, movimiento);
            }
            movimientos.add(movimiento);
        }
        return new ArrayList<>(movimientos);
    }

    /** Lee "generation-iv" desde species; si no hay datos, la calcula por el número de Pokédex. */
    private int obtenerGeneracion(Map<String, Object> especie, int idPokedex) {
        if (especie != null && especie.get("generation") != null) {
            String nombre = (String) mapa(especie.get("generation")).get("name"); // ej. "generation-iv"
            int gen = romanoAEntero(nombre.substring(nombre.indexOf('-') + 1));
            if (gen >= 1 && gen <= REGION_POR_GENERACION.length) {
                return gen;
            }
        }
        for (int i = 0; i < ULTIMO_ID_POR_GENERACION.length; i++) {
            if (idPokedex <= ULTIMO_ID_POR_GENERACION[i]) {
                return i + 1;
            }
        }
        return REGION_POR_GENERACION.length;
    }

    /** Busca la descripción de la Pokédex en español; si no existe, usa la de inglés. */
    private String obtenerDescripcion(Map<String, Object> especie) {
        if (especie == null) {
            return "Sin descripción disponible.";
        }
        String enIngles = null;
        for (Map<String, Object> entrada : lista(especie.get("flavor_text_entries"))) {
            String idioma = (String) mapa(entrada.get("language")).get("name");
            String texto = limpiarTexto((String) entrada.get("flavor_text"));
            if ("es".equals(idioma)) {
                return texto;
            }
            if ("en".equals(idioma) && enIngles == null) {
                enIngles = texto;
            }
        }
        return enIngles != null ? enIngles : "Sin descripción disponible.";
    }

    /** Usa el arte oficial; si no existe, el sprite normal. */
    private String obtenerImagen(Map<String, Object> datos) {
        Map<String, Object> sprites = mapa(datos.get("sprites"));
        Map<String, Object> other = mapa(sprites.get("other"));
        Object arteOficial = mapa(other.get("official-artwork")).get("front_default");
        if (arteOficial != null) {
            return (String) arteOficial;
        }
        return (String) sprites.get("front_default");
    }

    // =====================================================================
    // RUTAS REALES (encuentros de la PokeAPI)
    // =====================================================================

    /**
     * Devuelve los lugares de los juegos donde el Pokémon aparece en estado salvaje.
     * Puede devolver una lista vacía: la PokeAPI no tiene encuentros de algunos Pokémon
     * (iniciales, legendarios de evento, y casi todos los de Galar y Paldea).
     */
    private Encuentros obtenerEncuentros(int idPokedex, CacheImportacion cache) {
        Set<Ruta> rutas = new LinkedHashSet<>();
        Set<String> metodos = new LinkedHashSet<>();
        for (Map<String, Object> encuentro : getJsonList(POKEAPI + "/pokemon/" + idPokedex + "/encounters")) {
            // Métodos de encuentro (hierba alta, surf, caña...) en todas las versiones
            for (Map<String, Object> version : lista(encuentro.get("version_details"))) {
                for (Map<String, Object> detalle : lista(version.get("encounter_details"))) {
                    String metodo = (String) mapa(detalle.get("method")).get("name");
                    if (metodo != null) {
                        metodos.add(metodo);
                    }
                }
            }

            Map<String, Object> area = mapa(encuentro.get("location_area"));
            String nombreArea = (String) area.get("name");
            String urlArea = (String) area.get("url");
            if (nombreArea == null || urlArea == null) {
                continue;
            }
            Ruta ruta = cache.porArea.get(nombreArea);
            if (ruta == null) {
                ruta = resolverRutaDeArea(urlArea, cache);
                if (ruta == null) {
                    continue;
                }
                cache.porArea.put(nombreArea, ruta);
            }
            rutas.add(ruta);
        }
        return new Encuentros(new ArrayList<>(rutas), metodos);
    }

    // =====================================================================
    // FORMA DE OBTENCIÓN
    // =====================================================================

    /**
     * Define tipoObtencion y formaObtencion. Se revisa en este orden:
     * inicial, singular, fósil, legendario, salvaje/regalo, bebé (huevo), evolución, sin datos.
     */
    private void definirObtencion(Pokemon pokemon, Map<String, Object> especie, Encuentros encuentros) {
        int id = pokemon.getIdPokedex();
        String region = pokemon.getRegion();
        boolean legendario = especie != null && Boolean.TRUE.equals(especie.get("is_legendary"));
        boolean singular = especie != null && Boolean.TRUE.equals(especie.get("is_mythical"));
        boolean bebe = especie != null && Boolean.TRUE.equals(especie.get("is_baby"));
        String preEvolucion = especie != null ? (String) mapa(especie.get("evolves_from_species")).get("name") : null;

        List<Ruta> rutas = encuentros.rutas();
        Set<String> metodos = encuentros.metodos();
        boolean soloRegalo = !rutas.isEmpty() && !metodos.isEmpty()
                && metodos.stream().allMatch(m -> m.equals("gift") || m.equals("gift-egg"));
        String tambien = rutas.isEmpty() ? "" : " También aparece en " + describirLugares(rutas) + ".";

        String tipo;
        String forma;

        if (INICIALES.contains(id)) {
            tipo = "Inicial";
            forma = "Pokémon inicial: se elige al comenzar la aventura en la región de " + region + "." + tambien;
        } else if (singular) {
            tipo = "Singular";
            forma = "Pokémon singular: solo se podía obtener en eventos de distribución oficiales"
                    + " o con objetos especiales; no aparece en estado salvaje." + tambien;
        } else if (FOSILES.contains(id) && preEvolucion == null) {
            tipo = "Fósil";
            forma = "Se obtiene reviviendo un fósil en un laboratorio de la región de " + region + "." + tambien;
        } else if (legendario) {
            tipo = "Legendario";
            forma = rutas.isEmpty()
                    ? "Pokémon legendario: se encuentra una sola vez durante la historia del juego."
                    : "Pokémon legendario: aparece una sola vez en " + describirLugares(rutas) + ".";
        } else if (soloRegalo) {
            tipo = "Regalo";
            forma = "Se recibe como regalo de un personaje en " + describirLugares(rutas) + ".";
        } else if (!rutas.isEmpty()) {
            tipo = "Salvaje";
            forma = "Se puede capturar en estado salvaje en " + describirLugares(rutas)
                    + describirMetodos(metodos) + ".";
        } else if (bebe) {
            tipo = "Huevo";
            forma = "Pokémon bebé: se obtiene eclosionando un huevo de su evolución"
                    + " (no aparece en estado salvaje).";
        } else if (preEvolucion != null) {
            tipo = "Evolución";
            forma = "No aparece en estado salvaje: se obtiene evolucionando a "
                    + capitalizar(preEvolucion.replace('-', ' ')) + ".";
        } else if (pokemon.getGeneracion() != null && pokemon.getGeneracion() >= 8) {
            tipo = "Sin datos";
            forma = "La PokeAPI aún no tiene datos de captura de los juegos de " + region + ".";
        } else {
            tipo = "Sin datos";
            forma = "No hay información de captura disponible en la PokeAPI.";
        }

        // Fósiles evolucionados (Omastar, Kabutops...): se aclara de dónde vienen
        if (FOSILES.contains(id) && preEvolucion != null && rutas.isEmpty()) {
            tipo = "Fósil";
            forma = "Se obtiene evolucionando a " + capitalizar(preEvolucion.replace('-', ' '))
                    + ", que se revive a partir de un fósil.";
        }

        pokemon.setTipoObtencion(tipo);
        pokemon.setFormaObtencion(forma);
    }

    /** "3 lugares de Kanto y Johto" o "Bosque Verde (Kanto)" si es uno solo. */
    private String describirLugares(List<Ruta> rutas) {
        if (rutas.size() == 1) {
            Ruta r = rutas.get(0);
            return r.getNombre() + " (" + r.getRegion() + ")";
        }
        Set<String> regiones = new LinkedHashSet<>();
        for (Ruta r : rutas) {
            regiones.add(r.getRegion());
        }
        return rutas.size() + " lugares de " + unirConY(new ArrayList<>(regiones));
    }

    /** " (hierba alta y cuevas, surf, caña vieja)" */
    private String describirMetodos(Set<String> metodos) {
        List<String> traducidos = new ArrayList<>();
        for (String m : metodos) {
            String t = METODOS_EN_ESPANOL.getOrDefault(m, m.replace('-', ' '));
            if (!traducidos.contains(t)) {
                traducidos.add(t);
            }
        }
        return traducidos.isEmpty() ? "" : " (" + String.join(", ", traducidos) + ")";
    }

    private static String unirConY(List<String> partes) {
        if (partes.size() <= 1) {
            return String.join("", partes);
        }
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
    }

    /** Área -> lugar -> Ruta guardada en la BD (la crea si no existe). */
    private Ruta resolverRutaDeArea(String urlArea, CacheImportacion cache) {
        try {
            Map<String, Object> area = getJson(urlArea);
            String urlLugar = (String) mapa(area.get("location")).get("url");
            if (urlLugar == null) {
                return null;
            }
            Ruta enCache = cache.porLugar.get(urlLugar);
            if (enCache != null) {
                return enCache;
            }

            Map<String, Object> lugar = getJson(urlLugar);
            Integer idLugar = ((Number) lugar.get("id")).intValue();

            Ruta ruta = rutaRepository.findByIdPokeapi(idLugar).orElse(null);
            if (ruta != null) {
                cache.existentes++;
            } else {
                ruta = new Ruta();
                ruta.setIdPokeapi(idLugar);
                ruta.setNombre(nombreDelLugar(lugar));
                ruta.setRegion(nombreDeRegion(lugar));
                ruta.setDescripcion("Lugar de los juegos donde aparecen Pokémon salvajes (datos de PokeAPI)");
                ruta = rutaRepository.save(ruta);
                cache.creadas++;
            }
            cache.porLugar.put(urlLugar, ruta);
            return ruta;
        } catch (Exception e) {
            cache.fallidas++;
            log.warn("No se pudo obtener el lugar {}: {}", urlArea, e.getMessage());
            return null;
        }
    }

    /** Nombre en español si existe; si no, en inglés ("Route 2" -> "Ruta 2"); si no, el slug. */
    private String nombreDelLugar(Map<String, Object> lugar) {
        String enIngles = null;
        for (Map<String, Object> n : lista(lugar.get("names"))) {
            String idioma = (String) mapa(n.get("language")).get("name");
            if ("es".equals(idioma)) {
                return (String) n.get("name");
            }
            if ("en".equals(idioma)) {
                enIngles = (String) n.get("name");
            }
        }
        if (enIngles != null) {
            return enIngles.replaceFirst("^Route ", "Ruta ");
        }
        return capitalizar(((String) lugar.get("name")).replace('-', ' '));
    }

    private String nombreDeRegion(Map<String, Object> lugar) {
        Object region = lugar.get("region");
        if (region == null) {
            return "Otra";
        }
        String nombre = (String) mapa(region).get("name"); // ej. "kanto", "unova"
        return REGION_EN_ESPANOL.getOrDefault(nombre, capitalizar(nombre));
    }

    /** Nombres de las regiones de origen, en orden de generación. */
    public List<String> listarRegiones() {
        return List.of(REGION_POR_GENERACION);
    }

    // =====================================================================
    // MOVIMIENTOS
    // =====================================================================

    public ResultadoImportacion importarMovimientosMasivo(int total) {
        int importados = 0;
        int omitidos = 0;
        int fallidos = 0;

        for (int id = 1; id <= total; id++) {
            try {
                Map<String, Object> datos = getJson(POKEAPI + "/move/" + id);
                if (datos == null) {
                    fallidos++;
                    continue;
                }
                String nombre = (String) datos.get("name");
                Movimiento existente = movimientoRepository.findFirstByNombreIgnoreCase(nombre).orElse(null);
                if (existente != null) {
                    // Ya existe: no se vuelve a crear, pero sí se refresca su nombre en español
                    actualizarNombreEs(existente, datos);
                    omitidos++;
                    continue;
                }

                movimientoRepository.save(crearMovimiento(datos));
                importados++;
            } catch (Exception e) {
                fallidos++;
                log.warn("No se pudo importar el movimiento ID {}: {}", id, e.getMessage());
            }
        }
        log.info("Movimientos importados: {} (ya existían: {}, fallidos: {})", importados, omitidos, fallidos);
        return new ResultadoImportacion("movimientos", importados, omitidos, fallidos);
    }

    /** Arma un Movimiento (sin guardarlo) a partir del JSON de /move/{id}. */
    private Movimiento crearMovimiento(Map<String, Object> datos) {
        if (datos == null || datos.get("name") == null) {
            throw new IllegalStateException("La PokeAPI no devolvió datos del movimiento");
        }
        String claseDanio = (String) mapa(datos.get("damage_class")).get("name");
        Integer potencia = datos.get("power") instanceof Number numero ? numero.intValue() : 0;

        Movimiento movimiento = new Movimiento();
        movimiento.setNombre((String) datos.get("name"));
        movimiento.setNombreEs(nombreEnEspanol(datos.get("names")));
        movimiento.setCategoria(traducirCategoria(claseDanio != null ? claseDanio : "physical"));
        movimiento.setPotencia(potencia);
        movimiento.setEsCompetitivo(potencia > 80);
        return movimiento;
    }

    /**
     * Pone el nombre en español a un movimiento que ya existe, a partir del JSON de /move/{id}.
     * Solo guarda si cambió; el resto de campos no se toca (pueden haberse editado con PUT).
     */
    private Movimiento actualizarNombreEs(Movimiento movimiento, Map<String, Object> datos) {
        String nombreEs = datos != null ? nombreEnEspanol(datos.get("names")) : null;
        if (nombreEs == null || nombreEs.equals(movimiento.getNombreEs())) {
            return movimiento;
        }
        movimiento.setNombreEs(nombreEs);
        return movimientoRepository.save(movimiento);
    }

    /** Busca en "names" el nombre en español; si no existe, el de inglés; si tampoco, null. */
    private static String nombreEnEspanol(Object names) {
        String enIngles = null;
        for (Map<String, Object> n : lista(names)) {
            String idioma = (String) mapa(n.get("language")).get("name");
            if ("es".equals(idioma)) {
                return (String) n.get("name");
            }
            if ("en".equals(idioma)) {
                enIngles = (String) n.get("name");
            }
        }
        return enIngles;
    }

    private String traducirCategoria(String claseDanio) {
        return switch (claseDanio.toLowerCase()) {
            case "special" -> "Especial";
            case "status" -> "Estado";
            default -> "Físico";
        };
    }

    // =====================================================================
    // UTILIDADES PARA LEER EL JSON DE LA POKEAPI
    // =====================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String url) {
        return restTemplate.getForObject(url, Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getJsonList(String url) {
        List<Map<String, Object>> respuesta = restTemplate.getForObject(url, List.class);
        return respuesta != null ? respuesta : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        return valor instanceof Map ? (Map<String, Object>) valor : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lista(Object valor) {
        return valor instanceof List ? (List<Map<String, Object>>) valor : List.of();
    }

    /** Convierte decímetros/hectogramos de la PokeAPI a metros/kilogramos. */
    private static Double decimal(Object valor) {
        return valor instanceof Number numero ? numero.doubleValue() / 10.0 : null;
    }

    /** Los textos de la Pokédex traen saltos de línea y caracteres de control. */
    private static String limpiarTexto(String texto) {
        if (texto == null) {
            return null;
        }
        return texto.replaceAll("[\\n\\f\\r\\u00AD]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static String capitalizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        StringBuilder sb = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            if (!palabra.isEmpty()) {
                sb.append(Character.toUpperCase(palabra.charAt(0))).append(palabra.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static int romanoAEntero(String romano) {
        int total = 0;
        int anterior = 0;
        for (int i = romano.length() - 1; i >= 0; i--) {
            int valor = switch (Character.toLowerCase(romano.charAt(i))) {
                case 'i' -> 1;
                case 'v' -> 5;
                case 'x' -> 10;
                default -> 0;
            };
            total += valor < anterior ? -valor : valor;
            anterior = Math.max(anterior, valor);
        }
        return total;
    }
}
