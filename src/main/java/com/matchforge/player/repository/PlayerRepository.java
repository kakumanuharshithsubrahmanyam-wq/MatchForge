package com.matchforge.player.repository;

import java.util.Optional;
import java.util.UUID;

import com.matchforge.player.domain.Player;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Player p WHERE p.id = :id")
	Optional<Player> findByIdForUpdate(@Param("id") UUID id);

	Optional<Player> findByUsername(String username);

	boolean existsByUsername(String username);
}