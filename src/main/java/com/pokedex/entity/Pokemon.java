package com.pokedex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.List;

@Entity
@Table(name = "pokemon")
@Getter
@Setter
@NoArgsConstructor
public class Pokemon {

    @Id
    @Column(name = "id_pokedex")
    private Integer idPokedex;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Column
    private Integer generacion;

    @ManyToMany
    @JoinTable(
        name = "pokemon_tipo",
        joinColumns = @JoinColumn(name = "id_pokedex"),
        inverseJoinColumns = @JoinColumn(name = "id_tipo")
    )
    private List<Tipo> tipos;

    @ManyToMany
    @JoinTable(
        name = "pokemon_ruta",
        joinColumns = @JoinColumn(name = "id_pokedex"),
        inverseJoinColumns = @JoinColumn(name = "id_ruta")
    )
    private List<Ruta> rutas;
}