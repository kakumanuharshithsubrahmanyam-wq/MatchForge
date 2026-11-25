package com.matchforge.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.matchforge.auth.domain.Account;
import com.matchforge.auth.domain.AccountRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, UUID> {

	Optional<Account> findByUsername(String username);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Account a WHERE a.username = :username")
	Optional<Account> findByUsernameForUpdate(@Param("username") String username);

	boolean existsByUsername(String username);

	Optional<Account> findByPlayerId(UUID playerId);

	boolean existsByRole(AccountRole role);
}