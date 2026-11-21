package com.pokedex.service;

import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Tipo;
import com.pokedex.exception.PokemonNoReconocidoException;
import com.pokedex.repository.PokemonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * F20: identifica un Pokémon por foto. El orden importa y es parte del contrato:
 * límite por IP → validación de la foto → IA → búsqueda en la base → similares.
 * Si falla el límite o la validación, la IA no se llama (no gasta cuota).
 *
 * <p>La foto vive solo en memoria durante la petición: no se guarda ni se escribe en el log.
 * En el log queda únicamente el slug (el de la base), la confianza y el tiempo.
 */
@Service
public class IdentificacionService {

    private static final Logger log = LoggerFactory.getLogger(IdentificacionService.class);

    /** Por debajo de esta confianza se trata como "no hay Pokémon". */
    static final double UMBRAL_CONFIANZA = 0.6;

    public record TipoResumen(Long id, String nombre) {}

    public record PokemonResumen(Integer idPokedex, String nombre, String nombreEs, List<TipoResumen> tipos,
                                 Integer totalEstadisticas, String imagenUrl) {}

    public record SimilarResumen(Integer idPokedex, String nombre, String nombreEs, List<TipoResumen> tipos,
                                 Integer totalEstadisticas, String imagenUrl, String motivo) {}

    public record Identificacion(PokemonResumen identificado, double confianza, List<SimilarResumen> similares) {}

    private final LimiteIdentificaciones limite;
    private final ValidadorFoto validador;
    private final ServicioVision vision;
    private final PokemonRepository pokemonRepository;
    private final SimilaresService similaresService;

    public IdentificacionService(LimiteIdentificaciones limite, ValidadorFoto validador, ServicioVision vision,
                                 PokemonRepository pokemonRepository, SimilaresService similaresService) {
        this.limite = limite;
        this.validador = validador;
        this.vision = vision;
        this.pokemonRepository = pokemonRepository;
        this.similaresService = similaresService;
    }

    /**
     * @param ip   la de {@code request.getRemoteAddr()} (con el proxy de Render, ya es la real)
     * @param foto el campo {@code foto}; puede ser null si no llegó
     */
    public Identificacion identificar(String ip, MultipartFile foto) {
        long inicio = System.nanoTime();

        limite.registrar(ip);                                           // 429
        String tipo = validador.validar(foto);                          // 400
        byte[] bytes = leer(foto);
        ResultadoVision resultado = vision.identificar(bytes, tipo);    // 502

        if (!resultado.identificado() || resultado.slug() == null || resultado.slug().isBlank()
                || resultado.confianza() < UMBRAL_CONFIANZA) {
            registrar(null, resultado.confianza(), inicio);
            throw new PokemonNoReconocidoException();
        }
        Pokemon pokemon = pokemonRepository.buscarPorSlug(resultado.slug().trim(), PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        if (pokemon == null) {
            registrar(null, resultado.confianza(), inicio);
            throw new PokemonNoReconocidoException();
        }

        List<SimilarResumen> similares = similaresService.similaresDe(pokemon.getIdPokedex()).stream()
                .map(s -> new SimilarResumen(s.pokemon().getIdPokedex(), s.pokemon().getNombre(),
                        s.pokemon().getNombreEs(), tipos(s.pokemon()), s.pokemon().getTotalEstadisticas(),
                        s.pokemon().getImagenUrl(), s.motivo()))
                .toList();
        Identificacion respuesta = new Identificacion(
                new PokemonResumen(pokemon.getIdPokedex(), pokemon.getNombre(), pokemon.getNombreEs(), tipos(pokemon),
                        pokemon.getTotalEstadisticas(), pokemon.getImagenUrl()),
                resultado.confianza(), similares);
        registrar(pokemon.getNombre(), resultado.confianza(), inicio);
        return respuesta;
    }

    private static byte[] leer(MultipartFile foto) {
        try {
            return foto.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Los tipos son lazy: se leen dentro de la petición (open-in-view=true, ver CLAUDE.md)
    private static List<TipoResumen> tipos(Pokemon p) {
        List<Tipo> tipos = p.getTipos() == null ? List.of() : p.getTipos();
        return tipos.stream().map(t -> new TipoResumen(t.getId(), t.getNombre())).toList();
    }

    /** Solo slug (el de la base, no el texto de la IA), confianza y tiempo. Nunca la foto. */
    private static void registrar(String slug, double confianza, long inicioNanos) {
        long ms = (System.nanoTime() - inicioNanos) / 1_000_000;
        log.info("Identificación: slug={} confianza={} tiempoMs={}", slug == null ? "-" : slug, confianza, ms);
    }
}
