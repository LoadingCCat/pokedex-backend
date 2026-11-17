package com.pokedex.service;

import com.pokedex.entity.Pokemon;
import com.pokedex.entity.PokemonExcluido;
import com.pokedex.entity.Ruta;
import com.pokedex.entity.Tipo;
import com.pokedex.exception.CamposInvalidosException;
import com.pokedex.exception.ConflictException;
import com.pokedex.exception.ResourceNotFoundException;
import com.pokedex.repository.PokemonExcluidoRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.repository.TipoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Crear, editar y borrar Pokémon desde el panel (F15). Todo lo que pasa por aquí queda marcado
 * con editadoAMano, y lo borrado va a pokemon_excluido: la reimportación respeta las dos cosas.
 */
@Service
public class GestionPokemonService {

    private final PokemonRepository pokemonRepository;
    private final TipoRepository tipoRepository;
    private final RutaRepository rutaRepository;
    private final PokemonExcluidoRepository pokemonExcluidoRepository;

    public GestionPokemonService(PokemonRepository pokemonRepository, TipoRepository tipoRepository,
                                 RutaRepository rutaRepository, PokemonExcluidoRepository pokemonExcluidoRepository) {
        this.pokemonRepository = pokemonRepository;
        this.tipoRepository = tipoRepository;
        this.rutaRepository = rutaRepository;
        this.pokemonExcluidoRepository = pokemonExcluidoRepository;
    }

    @Transactional
    public Pokemon crear(DatosPokemon datos) {
        if (pokemonRepository.existsById(datos.idPokedex())) {
            throw new ConflictException("Ya existe un Pokémon con el número " + datos.idPokedex());
        }
        Relaciones relaciones = resolverRelaciones(datos);
        Pokemon pokemon = new Pokemon();
        pokemon.setIdPokedex(datos.idPokedex());
        pokemon.setNombre(datos.nombre());
        aplicar(pokemon, datos, relaciones);
        // Si se había borrado antes, al crearlo a mano deja de estar excluido
        pokemonExcluidoRepository.deleteById(datos.idPokedex());
        return pokemonRepository.save(pokemon);
    }

    @Transactional
    public Pokemon actualizar(int idPokedex, DatosPokemon datos) {
        Pokemon pokemon = pokemonRepository.findById(idPokedex)
                .orElseThrow(() -> new ResourceNotFoundException("Pokémon", idPokedex));
        Relaciones relaciones = resolverRelaciones(datos);
        aplicar(pokemon, datos, relaciones);
        return pokemonRepository.save(pokemon);
    }

    @Transactional
    public void eliminar(int idPokedex) {
        Pokemon pokemon = pokemonRepository.findById(idPokedex)
                .orElseThrow(() -> new ResourceNotFoundException("Pokémon", idPokedex));
        // Hibernate borra sus filas de pokemon_tipo, pokemon_ruta y pokemon_movimiento
        pokemonRepository.delete(pokemon);
        pokemonExcluidoRepository.save(new PokemonExcluido(idPokedex));
    }

    private record Relaciones(List<Tipo> tipos, List<Ruta> rutas) {
    }

    /** Busca los tipos y las rutas por nombre e id; los que no existen van a "campos". */
    private Relaciones resolverRelaciones(DatosPokemon datos) {
        Map<String, String> errores = new LinkedHashMap<>();

        List<Tipo> tipos = new ArrayList<>();
        List<String> desconocidos = new ArrayList<>();
        for (String nombre : datos.tipos()) {
            Tipo tipo = tipoRepository.findByNombre(nombre);
            if (tipo == null) {
                desconocidos.add(nombre);
            } else {
                tipos.add(tipo);
            }
        }
        if (!desconocidos.isEmpty()) {
            errores.put("tipos", "Tipo desconocido: " + String.join(", ", desconocidos));
        }

        List<Ruta> rutas = null;
        if (datos.rutas() != null) {
            rutas = rutaRepository.findAllById(datos.rutas());
            if (rutas.size() != datos.rutas().size()) {
                Set<Integer> encontradas = rutas.stream().map(Ruta::getIdRuta).collect(Collectors.toSet());
                String faltan = datos.rutas().stream().filter(id -> !encontradas.contains(id))
                        .map(String::valueOf).collect(Collectors.joining(", "));
                errores.put("rutas", "No existen las rutas: " + faltan);
            }
        }

        if (!errores.isEmpty()) {
            throw new CamposInvalidosException(errores);
        }
        return new Relaciones(tipos, rutas);
    }

    private void aplicar(Pokemon pokemon, DatosPokemon datos, Relaciones relaciones) {
        pokemon.setNombreEs(datos.nombreEs());
        if (datos.generacion() != null) {
            pokemon.setGeneracion(datos.generacion());
            pokemon.setRegion(PokemonService.regionDeGeneracion(datos.generacion()));
        }
        if (datos.enviado("descripcion")) {
            pokemon.setDescripcion(datos.descripcion());
        }
        if (datos.enviado("formaObtencion")) {
            pokemon.setFormaObtencion(datos.formaObtencion());
        }
        if (datos.enviado("imagenUrl")) {
            pokemon.setImagenUrl(datos.imagenUrl());
        }
        pokemon.setTipoObtencion(datos.tipoObtencion());
        pokemon.setAltura(datos.altura());
        pokemon.setPeso(datos.peso());
        pokemon.setPs(datos.ps());
        pokemon.setAtaque(datos.ataque());
        pokemon.setDefensa(datos.defensa());
        pokemon.setAtaqueEspecial(datos.ataqueEspecial());
        pokemon.setDefensaEspecial(datos.defensaEspecial());
        pokemon.setVelocidad(datos.velocidad());
        pokemon.setTipos(new ArrayList<>(relaciones.tipos()));
        if (relaciones.rutas() != null) {
            pokemon.setRutas(new ArrayList<>(relaciones.rutas()));
        } else if (pokemon.getRutas() == null) {
            pokemon.setRutas(new ArrayList<>());
        }
        pokemon.setEditadoAMano(true);
    }
}
