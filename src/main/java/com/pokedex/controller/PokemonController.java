package com.pokedex.controller;

import com.pokedex.entity.Movimiento;
import com.pokedex.entity.Pokemon;
import com.pokedex.exception.BadRequestException;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.PokemonFiltros;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.service.DatosPokemon;
import com.pokedex.service.EvolucionService;
import com.pokedex.service.FormasObtencion;
import com.pokedex.service.GestionPokemonService;
import com.pokedex.service.EvolucionService.EtapaEvolucion;
import com.pokedex.service.PokemonService;
import com.pokedex.service.ProgresoImportacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pokemons")
public class PokemonController {

    private static final int MAX_GENERACION = 9;
    private static final int MAX_MOVIMIENTOS = 900;
    private static final int MAX_TIPOS = 2;

    private final PokemonRepository pokemonRepository;
    private final RutaRepository rutaRepository;
    private final MovimientoRepository movimientoRepository;
    private final PokemonService pokemonService;
    private final EvolucionService evolucionService;

    private final GestionPokemonService gestionPokemonService;

    public PokemonController(PokemonRepository pokemonRepository, RutaRepository rutaRepository,
                             MovimientoRepository movimientoRepository, PokemonService pokemonService,
                             EvolucionService evolucionService, GestionPokemonService gestionPokemonService) {
        this.pokemonRepository = pokemonRepository;
        this.rutaRepository = rutaRepository;
        this.movimientoRepository = movimientoRepository;
        this.pokemonService = pokemonService;
        this.evolucionService = evolucionService;
        this.gestionPokemonService = gestionPokemonService;
    }

    // ---------------------------------------------------------------
    // GESTIÓN (F15, solo ADMIN): crear, editar y borrar desde el panel
    // ---------------------------------------------------------------

    // POST /api/pokemons  -> 201. idPokedex ocupado -> 409
    @PostMapping
    public ResponseEntity<Pokemon> crearPokemon(@RequestBody(required = false) Map<String, Object> cuerpo) {
        Pokemon creado = gestionPokemonService.crear(DatosPokemon.desde(cuerpo, true));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // PUT /api/pokemons/{id}  -> 200 y queda editadoAMano (la reimportación no lo pisa)
    @PutMapping("/{id}")
    public Pokemon actualizarPokemon(@PathVariable Integer id,
                                     @RequestBody(required = false) Map<String, Object> cuerpo) {
        return gestionPokemonService.actualizar(id, DatosPokemon.desde(cuerpo, false));
    }

    // DELETE /api/pokemons/{id}  -> 204; la reimportación no lo vuelve a crear
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarPokemon(@PathVariable Integer id) {
        gestionPokemonService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------
    // CONSULTAS (contrato para el frontend)
    // ---------------------------------------------------------------

    // Todas las listas de Pokémon se ordenan por idPokedex si no se envía ?sort= (@SortDefault):
    // sin un orden fijo, la BD devuelve las filas en cualquier orden y la paginación puede
    // repetir o saltarse Pokémon.

    // GET /api/pokemons?page=0&size=20&sort=idPokedex
    // Filtros opcionales que se combinan con Y: ?nombre=&generacion=&tipo=&region=&obtencion=
    // Ej. /api/pokemons?tipo=fire&generacion=1. Un filtro vacío (?tipo=) se ignora.
    // tipo se puede repetir hasta 2 veces (F14): ?tipo=fire&tipo=flying = tiene los dos.
    @GetMapping
    public Page<Pokemon> listarPokemons(@RequestParam(required = false) String nombre,
                                        @RequestParam(required = false) Integer generacion,
                                        @RequestParam(required = false) List<String> tipo,
                                        @RequestParam(required = false) String region,
                                        @RequestParam(required = false) String obtencion,
                                        @SortDefault("idPokedex") Pageable pageable) {
        nombre = limpiar(nombre);
        List<String> tipos = limpiarTipos(tipo);
        region = limpiar(region);
        obtencion = limpiar(obtencion);
        if (generacion != null) {
            validarGeneracion(generacion);
        }
        if (obtencion != null) {
            obtencion = normalizarObtencion(obtencion);
        }
        if (nombre == null && generacion == null && tipos.isEmpty() && region == null && obtencion == null) {
            return pokemonRepository.findAll(pageable);
        }
        return pokemonRepository.findAll(PokemonFiltros.con(nombre, generacion, tipos, region, obtencion), pageable);
    }

    /** Quita espacios; un filtro vacío o en blanco cuenta como no enviado. */
    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /**
     * Tipos del filtro (F14): sin vacíos y sin repetidos (sin importar mayúsculas).
     * Más de 2 distintos → 400. Lista vacía = sin filtro de tipo.
     */
    private static List<String> limpiarTipos(List<String> valores) {
        if (valores == null) {
            return List.of();
        }
        List<String> tipos = valores.stream()
                .map(PokemonController::limpiar)
                .filter(java.util.Objects::nonNull)
                .map(String::toLowerCase)
                .distinct()
                .toList();
        if (tipos.size() > MAX_TIPOS) {
            throw new BadRequestException("Se pueden filtrar como mucho " + MAX_TIPOS
                    + " tipos a la vez (por ejemplo ?tipo=fire&tipo=flying)");
        }
        return tipos;
    }

    private static void validarGeneracion(int generacion) {
        if (generacion < 1 || generacion > MAX_GENERACION) {
            throw new BadRequestException("La generación debe estar entre 1 y " + MAX_GENERACION);
        }
    }

    /** Acepta el valor con o sin tilde (fosil = Fósil) y devuelve el nombre oficial; 400 si no existe. */
    private static String normalizarObtencion(String tipoObtencion) {
        String oficial = FormasObtencion.oficial(tipoObtencion);
        if (oficial == null) {
            throw new BadRequestException(
                    "Tipo de obtención inválido. Valores: " + String.join(", ", FormasObtencion.VALORES));
        }
        return oficial;
    }

    // GET /api/pokemons/buscar?nombre=pika
    @GetMapping("/buscar")
    public Page<Pokemon> buscarPorNombre(@RequestParam String nombre, @SortDefault("idPokedex") Pageable pageable) {
        if (nombre.isBlank()) {
            throw new BadRequestException("El parámetro 'nombre' no puede estar vacío");
        }
        return pokemonRepository.buscarPorNombre(nombre.trim(), pageable);
    }

    // GET /api/pokemons/generacion/1
    @GetMapping("/generacion/{generacion}")
    public Page<Pokemon> listarPorGeneracion(@PathVariable Integer generacion, @SortDefault("idPokedex") Pageable pageable) {
        validarGeneracion(generacion);
        return pokemonRepository.findByGeneracion(generacion, pageable);
    }

    // GET /api/pokemons/{id}
    @GetMapping("/{id}")
    public Pokemon obtenerPorId(@PathVariable Integer id) {
        return pokemonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pokémon", id));
    }

    // GET /api/pokemons/{id}/movimientos  -> movimientos que puede aprender, ordenados por nombre
    @GetMapping("/{id}/movimientos")
    public List<Movimiento> listarMovimientos(@PathVariable Integer id) {
        if (!pokemonRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pokémon", id);
        }
        return movimientoRepository.buscarPorPokemon(id);
    }

    // GET /api/pokemons/{id}/evoluciones  -> cadena evolutiva completa, desde su etapa base
    @GetMapping("/{id}/evoluciones")
    public EtapaEvolucion obtenerEvoluciones(@PathVariable Integer id) {
        return evolucionService.obtenerCadena(id);
    }

    // GET /api/pokemons/tipo/fire
    @GetMapping("/tipo/{tipo}")
    public Page<Pokemon> listarPorTipo(@PathVariable String tipo, @SortDefault("idPokedex") Pageable pageable) {
        return pokemonRepository.findByTiposNombreIgnoreCase(tipo, pageable);
    }

    // GET /api/pokemons/region/kanto
    @GetMapping("/region/{region}")
    public Page<Pokemon> listarPorRegion(@PathVariable String region, @SortDefault("idPokedex") Pageable pageable) {
        return pokemonRepository.buscarPorRegion(region, pageable);
    }

    // GET /api/pokemons/obtencion/legendario  -> por forma de obtención
    // Valores: Inicial, Singular, Fósil, Legendario, Salvaje, Regalo, Huevo, Evolución, Sin datos
    @GetMapping("/obtencion/{tipoObtencion}")
    public Page<Pokemon> listarPorObtencion(@PathVariable String tipoObtencion, @SortDefault("idPokedex") Pageable pageable) {
        // Acepta el valor con o sin tilde: /obtencion/fosil = /obtencion/Fósil
        return pokemonRepository.findByTipoObtencionIgnoreCase(normalizarObtencion(tipoObtencion), pageable);
    }

    // GET /api/pokemons/ruta/{idRuta}  -> Pokémon que aparecen en una ruta concreta
    @GetMapping("/ruta/{idRuta}")
    public Page<Pokemon> listarPorRuta(@PathVariable Integer idRuta, @SortDefault("idPokedex") Pageable pageable) {
        if (!rutaRepository.existsById(idRuta)) {
            throw new ResourceNotFoundException("Ruta", idRuta);
        }
        return pokemonRepository.buscarPorRuta(idRuta, pageable);
    }

    // ---------------------------------------------------------------
    // IMPORTACIÓN DESDE POKEAPI (uso interno, no es parte del contrato)
    // Aceptan GET (para probar desde el navegador) y POST.
    // Corren en segundo plano (F10): responden 202 al instante y el avance se
    // consulta en GET /api/pokemons/importacion.
    // ---------------------------------------------------------------

    // /api/pokemons/importar-rango?total=151&movimientos=100
    @RequestMapping(value = "/importar-rango", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> importarRango(@RequestParam int total,
                                                             @RequestParam(defaultValue = "50") int movimientos) {
        if (total < 1 || total > PokemonService.MAX_POKEMON) {
            throw new BadRequestException("'total' debe estar entre 1 y " + PokemonService.MAX_POKEMON);
        }
        if (movimientos < 0 || movimientos > MAX_MOVIMIENTOS) {
            throw new BadRequestException("'movimientos' debe estar entre 0 y " + MAX_MOVIMIENTOS);
        }
        return respuestaImportacion(pokemonService.iniciarImportacion(total, movimientos));
    }

    // /api/pokemons/importar-todo  -> importación rápida (50 Pokémon y 50 movimientos)
    @RequestMapping(value = "/importar-todo", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> importarTodoAutomaticamente() {
        return respuestaImportacion(pokemonService.iniciarImportacion(50, 50));
    }

    // GET /api/pokemons/importacion -> avance de la importación en curso o de la última
    @GetMapping("/importacion")
    public ProgresoImportacion.Estado progresoImportacion() {
        return pokemonService.progresoImportacion();
    }

    private ResponseEntity<Map<String, Object>> respuestaImportacion(ProgresoImportacion.Estado progreso) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("mensaje", "Importación iniciada. Consulta el avance en GET /api/pokemons/importacion");
        respuesta.put("progreso", progreso);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(respuesta);
    }
}
