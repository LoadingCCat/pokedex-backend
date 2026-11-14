package com.pokedex.service;

import com.pokedex.entity.Evolucion;
import com.pokedex.entity.Movimiento;
import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Ruta;
import com.pokedex.entity.Tipo;
import com.pokedex.exception.ConflictException;
import com.pokedex.repository.EvolucionRepository;
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

        // Cadenas evolutivas: varios Pokémon comparten la misma cadena
        final Set<String> cadenasGuardadas = new HashSet<>();        // URL de la cadena
        final Set<String> cadenasFallidas = new HashSet<>();         // URL de la cadena
        final Map<String, String> objetosPorUrl = new HashMap<>();   // URL del objeto -> nombre en español
    }

    private final PokemonRepository pokemonRepository;
    private final TipoRepository tipoRepository;
    private final MovimientoRepository movimientoRepository;
    private final RutaRepository rutaRepository;
    private final EvolucionRepository evolucionRepository;
    private final RestTemplate restTemplate;

    public PokemonService(PokemonRepository pokemonRepository, TipoRepository tipoRepository,
                          MovimientoRepository movimientoRepository, RutaRepository rutaRepository,
                          EvolucionRepository evolucionRepository, RestTemplate restTemplate) {
        this.pokemonRepository = pokemonRepository;
        this.tipoRepository = tipoRepository;
        this.movimientoRepository = movimientoRepository;
        this.rutaRepository = rutaRepository;
        this.evolucionRepository = evolucionRepository;
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
            resultados.add(new ResultadoImportacion("cadenas-evolutivas", cache.cadenasGuardadas.size(),
                    0, cache.cadenasFallidas.size()));
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

        // Parte opcional: si la cadena falla, el Pokémon queda importado igual
        importarCadenaEvolutiva(especie, cache);
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
    // CADENAS EVOLUTIVAS (/evolution-chain/{id})
    // =====================================================================

    /** Métodos de evolución poco comunes de la PokeAPI (los habituales se tratan aparte). */
    private static final Map<String, String> METODOS_DE_EVOLUCION = Map.ofEntries(
        Map.entry("shed", "Al evolucionar Nincada, con un hueco libre en el equipo y una Poké Ball"),
        Map.entry("spin", "Girando sobre sí mismo"),
        Map.entry("tower-of-darkness", "Completando la Torre de las Sombras"),
        Map.entry("tower-of-waters", "Completando la Torre de las Aguas"),
        Map.entry("three-critical-hits", "Con tres golpes críticos en un mismo combate"),
        Map.entry("take-damage", "Tras recibir daño en combate"),
        Map.entry("agile-style-move", "Usando un movimiento en estilo rápido"),
        Map.entry("strong-style-move", "Usando un movimiento en estilo fuerte"),
        Map.entry("recoil-damage", "Tras recibir daño de retroceso"),
        Map.entry("use-move", "Usando un movimiento"),
        Map.entry("three-defeated-bisharp", "Tras derrotar a tres Bisharp líderes"),
        Map.entry("gimmighoul-coins", "Con 999 monedas de Gimmighoul"),
        Map.entry("in-battle-level-up", "Subiendo de nivel en combate"),
        Map.entry("meltan-candies", "Con 400 caramelos de Meltan")
        // "unclassified" y cualquier método nuevo de la PokeAPI salen como "Método especial"
    );

    /** Sufijos de las formas regionales en los nombres de forma de la PokeAPI (ej. "meowth-galar"). */
    private static final Map<String, String> FORMAS_REGIONALES = Map.of(
        "alola", "de Alola",
        "galar", "de Galar",
        "hisui", "de Hisui",
        "paldea", "de Paldea"
    );

    /**
     * Guarda la cadena evolutiva de la especie (todas sus etapas), una sola vez por importación.
     * No lanza excepciones: si falla, se registra y se conserva lo que hubiera en la BD.
     */
    private void importarCadenaEvolutiva(Map<String, Object> especie, CacheImportacion cache) {
        String url = especie != null ? (String) mapa(especie.get("evolution_chain")).get("url") : null;
        if (url == null || cache.cadenasGuardadas.contains(url)) {
            return;
        }
        try {
            Map<String, Object> cadena = getJson(url);
            if (cadena == null || !(cadena.get("id") instanceof Number idCadena)) {
                throw new IllegalStateException("La PokeAPI no devolvió la cadena");
            }
            guardarEtapa(mapa(cadena.get("chain")), null, null, idCadena.intValue(), cache);
            cache.cadenasGuardadas.add(url);
            cache.cadenasFallidas.remove(url);
        } catch (Exception e) {
            cache.cadenasFallidas.add(url);
            log.warn("No se pudo obtener la cadena evolutiva {}: {}", url, e.getMessage());
        }
    }

    /** Guarda una etapa de la cadena y, de forma recursiva, las etapas a las que evoluciona. */
    private void guardarEtapa(Map<String, Object> nodo, Integer idPreEvolucion, String nombrePreEvolucion,
                              int idCadena, CacheImportacion cache) {
        Map<String, Object> especie = mapa(nodo.get("species"));
        Integer idPokedex = idDeUrl((String) especie.get("url"));
        String nombre = (String) especie.get("name");
        if (idPokedex == null || nombre == null) {
            return;
        }
        Evolucion etapa = evolucionRepository.findById(idPokedex).orElseGet(Evolucion::new);
        etapa.setIdPokedex(idPokedex);
        etapa.setNombre(nombre);
        etapa.setIdCadena(idCadena);
        etapa.setIdPreEvolucion(idPreEvolucion);
        etapa.setCondicion(idPreEvolucion == null
                ? null
                : describirCondicion(lista(nodo.get("evolution_details")), nombrePreEvolucion, nombre, cache));
        evolucionRepository.save(etapa);

        for (Map<String, Object> siguiente : lista(nodo.get("evolves_to"))) {
            guardarEtapa(siguiente, idPokedex, nombre, idCadena, cache);
        }
    }

    /**
     * Si el detalle solo aplica a una forma distinta de la normal (regional u otra), devuelve
     * cuál: "Meowth de Galar". Devuelve null si aplica a la forma normal de la especie.
     * La PokeAPI lo indica en required_pokemon_form (quién evoluciona) y evolved_pokemon_form
     * (en qué forma se convierte); si coinciden con el nombre de la especie, es la forma normal.
     */
    private static String formaEspecial(Map<String, Object> detalle, String nombrePreEvolucion, String nombre) {
        String requerida = (String) mapa(detalle.get("required_pokemon_form")).get("name");
        if (requerida != null && !requerida.equals(nombrePreEvolucion)) {
            return describirForma(requerida, nombrePreEvolucion);
        }
        String resultante = (String) mapa(detalle.get("evolved_pokemon_form")).get("name");
        if (resultante != null && !resultante.equals(nombre)) {
            return describirForma(resultante, nombre);
        }
        return null;
    }

    /** "meowth-galar" -> "Meowth de Galar"; otras formas, el slug legible ("Basculin White Striped"). */
    private static String describirForma(String forma, String especie) {
        if (especie != null && forma.startsWith(especie + "-")) {
            String region = FORMAS_REGIONALES.get(forma.substring(especie.length() + 1));
            if (region != null) {
                return capitalizar(especie.replace('-', ' ')) + " " + region;
            }
        }
        return capitalizar(forma.replace('-', ' '));
    }

    /**
     * Texto en español de cómo se llega a una etapa. La PokeAPI repite la condición por cada
     * juego y lista una por cada lugar: se quitan las repetidas y los lugares de una misma
     * forma se agrupan ("Subiendo de nivel en Bosque Vetusto / Pinwheel Forest o Usando Piedra Hoja").
     *
     * Las formas regionales están fuera del alcance (F7): si hay alguna forma de evolucionar
     * de la especie normal, las que solo aplican a otra forma se descartan (Slowpoke de Galar
     * con el Brazal Galanuez). Si solo existe esa vía (Perrserker), se conserva y se aclara:
     * "Nivel 28 (solo Meowth de Galar)".
     */
    private String describirCondicion(List<Map<String, Object>> detalles, String nombrePreEvolucion,
                                      String nombre, CacheImportacion cache) {
        boolean hayFormaNormal = detalles.stream()
                .anyMatch(d -> formaEspecial(d, nombrePreEvolucion, nombre) == null);

        Map<String, Set<String>> lugaresPorForma = new java.util.LinkedHashMap<>();
        for (Map<String, Object> detalle : detalles) {
            String forma = formaEspecial(detalle, nombrePreEvolucion, nombre);
            if (forma != null && hayFormaNormal) {
                continue;
            }
            String texto = describirDetalle(detalle, cache) + (forma != null ? " (solo " + forma + ")" : "");
            Set<String> lugares = lugaresPorForma.computeIfAbsent(texto, k -> new LinkedHashSet<>());
            Map<String, Object> lugar = mapa(detalle.get("location"));
            if (lugar.get("name") != null) {
                lugares.add(nombreDeLugarDeEvolucion(lugar));
            }
        }
        if (lugaresPorForma.isEmpty()) {
            return "Sin datos en la PokeAPI";
        }
        List<String> formas = new ArrayList<>();
        for (Map.Entry<String, Set<String>> forma : lugaresPorForma.entrySet()) {
            formas.add(forma.getValue().isEmpty()
                    ? forma.getKey()
                    : forma.getKey() + " en " + String.join(" / ", forma.getValue()));
        }
        return String.join(" o ", formas);
    }

    /** Una entrada de "evolution_details" (sin el lugar, que se agrupa aparte). */
    private String describirDetalle(Map<String, Object> detalle, CacheImportacion cache) {
        String metodo = (String) mapa(detalle.get("trigger")).get("name");
        Integer nivel = entero(detalle.get("min_level"));
        List<String> partes = new ArrayList<>();

        switch (metodo != null ? metodo : "") {
            case "level-up" -> partes.add(nivel != null ? "Nivel " + nivel : "Subiendo de nivel");
            case "trade" -> partes.add("Por intercambio");
            case "use-item" -> {
                Map<String, Object> objeto = mapa(detalle.get("item"));
                partes.add(objeto.get("name") != null ? "Usando " + nombreDeObjeto(objeto, cache) : "Usando un objeto");
            }
            default -> {
                partes.add(METODOS_DE_EVOLUCION.getOrDefault(metodo, "Método especial"));
                if (nivel != null) {
                    partes.add("nivel " + nivel);
                }
            }
        }

        Map<String, Object> objetoEquipado = mapa(detalle.get("held_item"));
        if (objetoEquipado.get("name") != null) {
            partes.add("llevando " + nombreDeObjeto(objetoEquipado, cache));
        }
        if (entero(detalle.get("min_happiness")) != null) {
            partes.add("con amistad alta");
        }
        if (entero(detalle.get("min_affection")) != null) {
            partes.add("con afecto alto");
        }
        if (entero(detalle.get("min_beauty")) != null) {
            partes.add("con belleza alta");
        }
        if (detalle.get("time_of_day") instanceof String momento) {
            switch (momento) {
                case "day" -> partes.add("de día");
                case "night" -> partes.add("de noche");
                case "dusk" -> partes.add("al atardecer");
                default -> { } // "" = a cualquier hora
            }
        }
        String movimiento = (String) mapa(detalle.get("known_move")).get("name");
        if (movimiento != null) {
            partes.add("conociendo " + nombreDeMovimiento(movimiento));
        }
        String tipoMovimiento = (String) mapa(detalle.get("known_move_type")).get("name");
        if (tipoMovimiento != null) {
            partes.add("conociendo un movimiento de tipo " + tipoMovimiento);
        }
        String movimientoUsado = (String) mapa(detalle.get("used_move")).get("name");
        if (movimientoUsado != null) {
            Integer veces = entero(detalle.get("min_move_count"));
            partes.add("tras usar " + nombreDeMovimiento(movimientoUsado)
                    + (veces != null ? " " + veces + " veces" : ""));
        }
        Integer genero = entero(detalle.get("gender"));
        if (genero != null) {
            partes.add(genero == 1 ? "solo hembras" : "solo machos");
        }
        Integer estadisticas = entero(detalle.get("relative_physical_stats"));
        if (estadisticas != null) {
            partes.add(estadisticas > 0 ? "con más ataque que defensa"
                    : estadisticas < 0 ? "con más defensa que ataque" : "con ataque y defensa iguales");
        }
        String enEquipo = (String) mapa(detalle.get("party_species")).get("name");
        if (enEquipo != null) {
            partes.add("con " + capitalizar(enEquipo.replace('-', ' ')) + " en el equipo");
        }
        String tipoEnEquipo = (String) mapa(detalle.get("party_type")).get("name");
        if (tipoEnEquipo != null) {
            partes.add("con un Pokémon de tipo " + tipoEnEquipo + " en el equipo");
        }
        String aCambioDe = (String) mapa(detalle.get("trade_species")).get("name");
        if (aCambioDe != null) {
            partes.add("a cambio de " + capitalizar(aCambioDe.replace('-', ' ')));
        }
        if (Boolean.TRUE.equals(detalle.get("needs_overworld_rain"))) {
            partes.add("con lluvia");
        }
        if (Boolean.TRUE.equals(detalle.get("turn_upside_down"))) {
            partes.add("con la consola boca abajo");
        }
        return String.join(", ", partes);
    }

    /** Nombre en español del objeto (de /item/{id} -> names); se pide una sola vez por importación. */
    private String nombreDeObjeto(Map<String, Object> objeto, CacheImportacion cache) {
        String slug = (String) objeto.get("name");
        String url = (String) objeto.get("url");
        String respaldo = capitalizar(slug.replace('-', ' '));
        if (url == null) {
            return respaldo;
        }
        String enCache = cache.objetosPorUrl.get(url);
        if (enCache != null) {
            return enCache;
        }
        String nombre = respaldo;
        try {
            Map<String, Object> datos = getJson(url);
            String traducido = datos != null ? nombreEnEspanol(datos.get("names")) : null;
            if (traducido != null) {
                nombre = traducido;
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener el nombre del objeto {}: {}", slug, e.getMessage());
        }
        cache.objetosPorUrl.put(url, nombre);
        return nombre;
    }

    /** nombreEs del movimiento si ya está en la BD; si no, el slug legible. */
    private String nombreDeMovimiento(String slug) {
        return movimientoRepository.findFirstByNombreIgnoreCase(slug)
                .map(Movimiento::getNombreEs)
                .orElse(capitalizar(slug.replace('-', ' ')));
    }

    /** Nombre de la Ruta si el lugar ya está importado; si no, el slug legible. */
    private String nombreDeLugarDeEvolucion(Map<String, Object> lugar) {
        Integer idLugar = idDeUrl((String) lugar.get("url"));
        String respaldo = capitalizar(((String) lugar.get("name")).replace('-', ' '));
        if (idLugar == null) {
            return respaldo;
        }
        return rutaRepository.findByIdPokeapi(idLugar).map(Ruta::getNombre).orElse(respaldo);
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

    private static Integer entero(Object valor) {
        return valor instanceof Number numero ? numero.intValue() : null;
    }

    /** Último número de una URL de la PokeAPI: ".../pokemon-species/133/" -> 133. */
    private static Integer idDeUrl(String url) {
        if (url == null) {
            return null;
        }
        String[] partes = url.split("/");
        try {
            return Integer.valueOf(partes[partes.length - 1]);
        } catch (NumberFormatException e) {
            return null;
        }
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
