package com.pokedex.service;

import com.pokedex.entity.Movimiento;
import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Ruta;
import com.pokedex.entity.Tipo;
import com.pokedex.repository.MovimientoRepository;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.RutaRepository;
import com.pokedex.repository.TipoRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@SuppressWarnings("rawtypes")
public class PokemonService {

    private final PokemonRepository pokemonRepository;
    private final TipoRepository tipoRepository;
    private final MovimientoRepository movimientoRepository;
    private final RutaRepository rutaRepository;

    public PokemonService(PokemonRepository pokemonRepository, TipoRepository tipoRepository,
                          MovimientoRepository movimientoRepository, RutaRepository rutaRepository) {
        this.pokemonRepository = pokemonRepository;
        this.tipoRepository = tipoRepository;
        this.movimientoRepository = movimientoRepository;
        this.rutaRepository = rutaRepository;
    }

    // Importar Pokémon masivamente desde PokeAPI
    public void importarPokemonsMasivo(int total) {
        RestTemplate restTemplate = new RestTemplate();
        for (int i = 1; i <= total; i++) {
            try {
                String url = "https://pokeapi.co/api/v2/pokemon/" + i;
                Map response = restTemplate.getForObject(url, Map.class);
                
                if (response != null) {
                    Integer idPokedex = (Integer) response.get("id");
                    String nombre = (String) response.get("name");
                    
                    int generacion = 1;
                    if (idPokedex > 151 && idPokedex <= 251) generacion = 2;
                    else if (idPokedex > 251 && idPokedex <= 386) generacion = 3;
                    else if (idPokedex > 386 && idPokedex <= 493) generacion = 4;
                    else if (idPokedex > 493) generacion = 5;

                    List typesList = (List) response.get("types");
                    Set<Tipo> tiposPokemon = new HashSet<>();
                    
                    if (typesList != null) {
                        for (Object tObj : typesList) {
                            Map tMap = (Map) tObj;
                            Map typeInfo = (Map) tMap.get("type");
                            String nombreTipo = (String) typeInfo.get("name");
                            
                            Tipo tipo = tipoRepository.findByNombre(nombreTipo);
                            if (tipo == null) {
                                tipo = new Tipo();
                                tipo.setNombre(nombreTipo);
                                tipo = tipoRepository.save(tipo);
                            }
                            tiposPokemon.add(tipo);
                        }
                    }

                    Pokemon pokemon = new Pokemon();
                    pokemon.setIdPokedex(idPokedex);
                    pokemon.setNombre(nombre);
                    pokemon.setDescripcion("Importado automáticamente desde PokeAPI");
                    pokemon.setGeneracion(generacion);
                    pokemon.setTipos(new ArrayList<>(tiposPokemon));

                    pokemonRepository.save(pokemon);
                }
            } catch (Exception e) {
                System.out.println("Error al importar el Pokémon ID " + i + ": " + e.getMessage());
            }
        }
    }

    // Importar Movimientos masivamente desde PokeAPI
    public void importarMovimientosMasivo(int total) {
        RestTemplate restTemplate = new RestTemplate();
        for (int i = 1; i <= total; i++) {
            try {
                String url = "https://pokeapi.co/api/v2/move/" + i;
                Map response = restTemplate.getForObject(url, Map.class);
                
                if (response != null) {
                    String nombre = (String) response.get("name");
                    
                    Map damageClass = (Map) response.get("damage_class");
                    String categoriaOriginal = damageClass != null ? (String) damageClass.get("name") : "unknown";
                    
                    String categoria = "Físico";
                    if ("special".equalsIgnoreCase(categoriaOriginal)) categoria = "Especial";
                    if ("status".equalsIgnoreCase(categoriaOriginal)) categoria = "Estado";

                    Object potenciaObj = response.get("power");
                    Integer potencia = potenciaObj != null ? (Integer) potenciaObj : 0;

                    if (movimientoRepository.findAll().stream().noneMatch(m -> m.getNombre().equalsIgnoreCase(nombre))) {
                        Movimiento movimiento = new Movimiento();
                        movimiento.setNombre(nombre);
                        movimiento.setCategoria(categoria);
                        movimiento.setPotencia(potencia);
                        movimiento.setEsCompetitivo(potencia > 80);
                        
                        movimientoRepository.save(movimiento);
                    }
                }
            } catch (Exception e) {
                System.out.println("No se pudo importar el movimiento ID " + i);
            }
        }
    }

    // Importar Rutas y Regiones automáticas
    public void importarRutasAutomaticas() {
        String[] regionesKanto = {"Ruta 1", "Ruta 2", "Ruta 3", "Bosque Verde", "Cueva Celeste"};
        String[] regionesJohto = {"Ruta 29", "Ruta 30", "Torre Quemada", "Cueva Helada"};

        guardarRutasPorRegion("Kanto", regionesKanto);
        guardarRutasPorRegion("Johto", regionesJohto);
    }

    private void guardarRutasPorRegion(String regionNombre, String[] nombresRutas) {
        for (String nombreRuta : nombresRutas) {
            boolean existe = rutaRepository.findAll().stream()
                    .anyMatch(r -> r.getNombre().equalsIgnoreCase(nombreRuta));
            if (!existe) {
                Ruta ruta = new Ruta();
                ruta.setNombre(nombreRuta);
                ruta.setRegion(regionNombre);
                ruta.setDescripcion("Zona de exploración automática de la región de " + regionNombre);
                rutaRepository.save(ruta);
            }
        }
    }
}