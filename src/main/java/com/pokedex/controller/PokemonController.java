package com.pokedex.controller;

import com.pokedex.entity.Movimiento;
import com.pokedex.entity.Pokemon;
import com.pokedex.exception.BadRequestException;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.service.PokemonService;
import com.pokedex.service.PokemonService.ResultadoImportacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pokemons")
public class PokemonController {

    private static final int MAX_GENERACION = 9;
    private static final int MAX_MOVIMIENTOS = 900;

    private final PokemonRepository pokemonRepository;
    private final RutaRepository rutaRepository;
    private final MovimientoRepository movimientoRepository;
    private final PokemonService pokemonService;

    public PokemonController(PokemonRepository pokemonRepository, RutaRepository rutaRepository,
                             MovimientoRepository movimientoRepository, PokemonService pokemonService) {
        this.pokemonRepository = pokemonRepository;
        this.rutaRepository = rutaRepository;
        this.movimientoRepository = movimientoRepository;
        this.pokemonService = pokemonService;
    }

    // ---------------------------------------------------------------
    // CONSULTAS (contrato para el frontend)
    // ---------------------------------------------------------------

    // GET /api/pokemons?page=0&size=20&sort=idPokedex
    @GetMapping
    public Page<Pokemon> listarPokemons(Pageable pageable) {
        return pokemonRepository.findAll(pageable);
    }

    // GET /api/pokemons/buscar?nombre=pika
    @GetMapping("/buscar")
    public Page<Pokemon> buscarPorNombre(@RequestParam String nombre, Pageable pageable) {
        if (nombre.isBlank()) {
            throw new BadRequestException("El parámetro 'nombre' no puede estar vacío");
        }
        return pokemonRepository.buscarPorNombre(nombre.trim(), pageable);
    }

    // GET /api/pokemons/generacion/1
    @GetMapping("/generacion/{generacion}")
    public Page<Pokemon> listarPorGeneracion(@PathVariable Integer generacion, Pageable pageable) {
        if (generacion < 1 || generacion > MAX_GENERACION) {
            throw new BadRequestException("La generación debe estar entre 1 y " + MAX_GENERACION);
        }
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

    // GET /api/pokemons/tipo/fire
    @GetMapping("/tipo/{tipo}")
    public Page<Pokemon> listarPorTipo(@PathVariable String tipo, Pageable pageable) {
        return pokemonRepository.findByTiposNombreIgnoreCase(tipo, pageable);
    }

    // GET /api/pokemons/region/kanto
    @GetMapping("/region/{region}")
    public Page<Pokemon> listarPorRegion(@PathVariable String region, Pageable pageable) {
        return pokemonRepository.buscarPorRegion(region, pageable);
    }

    // GET /api/pokemons/obtencion/legendario  -> por forma de obtención
    // Valores: Inicial, Singular, Fósil, Legendario, Salvaje, Regalo, Huevo, Evolución, Sin datos
    @GetMapping("/obtencion/{tipoObtencion}")
    public Page<Pokemon> listarPorObtencion(@PathVariable String tipoObtencion, Pageable pageable) {
        // Acepta el valor con o sin tilde: /obtencion/fosil = /obtencion/Fósil
        String buscado = sinTildes(tipoObtencion.trim());
        String valor = TIPOS_OBTENCION.stream()
                .filter(t -> sinTildes(t).equalsIgnoreCase(buscado))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Tipo de obtención inválido. Valores: " + String.join(", ", TIPOS_OBTENCION)));
        return pokemonRepository.findByTipoObtencionIgnoreCase(valor, pageable);
    }

    private static final List<String> TIPOS_OBTENCION = List.of(
            "Inicial", "Singular", "Fósil", "Legendario", "Salvaje", "Regalo", "Huevo", "Evolución", "Sin datos");

    private static String sinTildes(String texto) {
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // GET /api/pokemons/ruta/{idRuta}  -> Pokémon que aparecen en una ruta concreta
    @GetMapping("/ruta/{idRuta}")
    public Page<Pokemon> listarPorRuta(@PathVariable Integer idRuta, Pageable pageable) {
        if (!rutaRepository.existsById(idRuta)) {
            throw new ResourceNotFoundException("Ruta", idRuta);
        }
        return pokemonRepository.buscarPorRuta(idRuta, pageable);
    }

    // ---------------------------------------------------------------
    // IMPORTACIÓN DESDE POKEAPI (uso interno, no es parte del contrato)
    // Aceptan GET (para probar desde el navegador) y POST.
    // ---------------------------------------------------------------

    // /api/pokemons/importar-rango?total=151&movimientos=100
    @RequestMapping(value = "/importar-rango", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> importarRango(@RequestParam int total,
                                             @RequestParam(defaultValue = "50") int movimientos) {
        if (total < 1 || total > PokemonService.MAX_POKEMON) {
            throw new BadRequestException("'total' debe estar entre 1 y " + PokemonService.MAX_POKEMON);
        }
        if (movimientos < 0 || movimientos > MAX_MOVIMIENTOS) {
            throw new BadRequestException("'movimientos' debe estar entre 0 y " + MAX_MOVIMIENTOS);
        }
        return respuestaImportacion(pokemonService.importarTodo(total, movimientos));
    }

    // /api/pokemons/importar-todo  -> importación rápida (50 Pokémon y 50 movimientos)
    @RequestMapping(value = "/importar-todo", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> importarTodoAutomaticamente() {
        return respuestaImportacion(pokemonService.importarTodo(50, 50));
    }

    private Map<String, Object> respuestaImportacion(List<ResultadoImportacion> resultados) {
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("mensaje", "Importación finalizada");
        respuesta.put("resultados", resultados);
        return respuesta;
    }
}
