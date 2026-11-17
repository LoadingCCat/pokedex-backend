package com.pokedex.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Pokémon borrado a mano con DELETE /api/pokemons/{id} (F15). La importación lo salta, para no
 * volver a crearlo sin aviso (regla de B.18 y B.28). Crearlo con POST lo saca de aquí; también
 * se puede borrar la fila y reimportar.
 */
@Entity
@Table(name = "pokemon_excluido")
@Getter
@NoArgsConstructor
public class PokemonExcluido {

    @Id
    @Column(name = "id_pokedex")
    private Integer idPokedex;

    public PokemonExcluido(Integer idPokedex) {
        this.idPokedex = idPokedex;
    }
}
