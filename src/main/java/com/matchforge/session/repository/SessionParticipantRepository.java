package com.matchforge.session.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.matchforge.session.domain.SessionParticipant;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, UUID> {

	List<SessionParticipant> findBySessionId(UUID sessionId);

	Optional<SessionParticipant> findBySessionIdAndPlayerId(UUID sessionId, UUID playerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM SessionParticipant p WHERE p.sessionId = :sessionId AND p.playerId = :playerId")
	Optional<SessionParticipant> findForUpdate(@Param("sessionId") UUID sessionId, @Param("playerId") UUID playerId);
}