package com.pokedex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;

@Entity
@Table(name = "tipo")
@Getter
@Setter
@NoArgsConstructor
public class Tipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;

    @ManyToMany(mappedBy = "tipos")
    @JsonIgnore
    private Set<Pokemon> pokemons;

    public Tipo(String nombre) {
        this.nombre = nombre;
    }
}