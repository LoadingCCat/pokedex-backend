package com.pokedex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "movimiento")
@Getter
@Setter
@NoArgsConstructor
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Integer idMovimiento;

    @Column(nullable = false, length = 100)
    private String nombre;

    // Nombre en español (de /move/{id} -> names). null hasta reimportar o si se creó a mano sin él
    @Column(name = "nombre_es", length = 100)
    private String nombreEs;

    @Column(nullable = false, length = 50)
    private String categoria;

    @Column
    private Integer potencia;

    @Column(name = "es_competitivo")
    private Boolean esCompetitivo = false;
}