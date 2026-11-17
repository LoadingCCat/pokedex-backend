package com.pokedex.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Lugar de la PokeAPI que se borró a mano con DELETE /api/rutas/{id} (F15). La importación
 * lo salta, para que no vuelva a aparecer (misma regla que B.18 y B.28: no pisar los
 * cambios hechos a mano). Para recuperarlo, se borra la fila y se reimporta.
 */
@Entity
@Table(name = "ruta_excluida")
@Getter
@NoArgsConstructor
public class RutaExcluida {

    @Id
    @Column(name = "id_pokeapi")
    private Integer idPokeapi;

    public RutaExcluida(Integer idPokeapi) {
        this.idPokeapi = idPokeapi;
    }
}
