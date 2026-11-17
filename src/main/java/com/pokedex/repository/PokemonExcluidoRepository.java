package com.pokedex.repository;

import com.pokedex.entity.PokemonExcluido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PokemonExcluidoRepository extends JpaRepository<PokemonExcluido, Integer> {
}
