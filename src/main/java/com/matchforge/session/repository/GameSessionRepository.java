package com.matchforge.session.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.matchforge.session.domain.GameSession;
import com.matchforge.session.domain.SessionStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {

	Optional<GameSession> findByMatchId(UUID matchId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM GameSession s WHERE s.id = :sessionId")
	Optional<GameSession> findByIdForUpdate(@Param("sessionId") UUID sessionId);

	@Query(
			"""
			SELECT s FROM GameSession s
			WHERE s.status IN (com.matchforge.session.domain.SessionStatus.ACTIVE, com.matchforge.session.domain.SessionStatus.PAUSED)
			""")
	List<GameSession> findSessionsForHeartbeatMaintenance();
}