package com.pokedex.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // ---- Estadísticas base (de /pokemon/{id} -> stats en la PokeAPI) ----
    @Column
    private Integer ps;

    @Column
    private Integer ataque;

    @Column
    private Integer defensa;

    @Column(name = "ataque_especial")
    private Integer ataqueEspecial;

    @Column(name = "defensa_especial")
    private Integer defensaEspecial;

    @Column
    private Integer velocidad;

    /** Suma de las 6 estadísticas base. Se calcula, no se guarda en la BD. null si falta alguna. */
    public Integer getTotalEstadisticas() {
        if (ps == null || ataque == null || defensa == null
                || ataqueEspecial == null || defensaEspecial == null || velocidad == null) {
            return null;
        }
        return ps + ataque + defensa + ataqueEspecial + defensaEspecial + velocidad;
    }

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

    // Movimientos que puede aprender. No se incluyen en el JSON del Pokémon porque son
    // muchos (Pikachu tiene más de 100); se consultan en GET /api/pokemons/{id}/movimientos
    @ManyToMany
    @JoinTable(
        name = "pokemon_movimiento",
        joinColumns = @JoinColumn(name = "id_pokedex"),
        inverseJoinColumns = @JoinColumn(name = "id_movimiento")
    )
    @JsonIgnore
    private List<Movimiento> movimientos;
}
