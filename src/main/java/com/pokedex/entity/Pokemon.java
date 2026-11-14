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

    // Región de origen según la generación (Kanto, Johto, Hoenn...)
    @Column(length = 50)
    private String region;

    // Categoría de cómo se consigue: Inicial, Singular, Fósil, Legendario, Salvaje,
    // Regalo, Huevo, Evolución o Sin datos
    @Column(name = "tipo_obtencion", length = 30)
    private String tipoObtencion;

    // Explicación de cómo se consigue el Pokémon en los juegos
    @Column(name = "forma_obtencion", columnDefinition = "TEXT")
    private String formaObtencion;

    // URL del arte oficial del Pokémon (para mostrar la imagen en el frontend)
    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    // Altura en metros
    @Column
    private Double altura;

    // Peso en kilogramos
    @Column
    private Double peso;

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
