package com.pokedex.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.List;

@Entity
@Table(name = "ruta")
@Getter
@Setter
@NoArgsConstructor
public class Ruta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ruta")
    private Integer idRuta;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 50)
    private String region;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    // ID del lugar ("location") en la PokeAPI. Es null en las rutas creadas a mano con POST.
    @Column(name = "id_pokeapi", unique = true)
    private Integer idPokeapi;

    @ManyToMany(mappedBy = "rutas")
    @JsonIgnore
    private List<Pokemon> pokemons;
}
