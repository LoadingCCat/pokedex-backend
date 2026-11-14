package com.pokedex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Una etapa de una cadena evolutiva (de /evolution-chain/{id} en la PokeAPI).
 *
 * Va en una tabla aparte y no dentro de Pokemon porque una cadena puede incluir Pokémon
 * que todavía no se han importado (ej. Pichu si solo se importó la generación 1).
 */
@Entity
@Table(name = "evolucion")
@Getter
@Setter
@NoArgsConstructor
public class Evolucion {

    // Número de Pokédex de la etapa (no es una clave foránea: el Pokémon puede no estar importado)
    @Id
    @Column(name = "id_pokedex")
    private Integer idPokedex;

    // Slug en inglés de la especie (ej. "ivysaur")
    @Column(nullable = false, length = 100)
    private String nombre;

    // Identificador de la cadena en la PokeAPI: todas las etapas de una cadena lo comparten
    @Column(name = "id_cadena", nullable = false)
    private Integer idCadena;

    // De qué Pokémon evoluciona; null en la etapa base de la cadena
    @Column(name = "id_pre_evolucion")
    private Integer idPreEvolucion;

    // Cómo se llega a esta etapa, en español (ej. "Nivel 16"); null en la etapa base
    @Column(columnDefinition = "TEXT")
    private String condicion;
}
