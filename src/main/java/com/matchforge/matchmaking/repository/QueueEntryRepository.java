package com.matchforge.matchmaking.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.matchforge.matchmaking.domain.QueueEntry;
import com.matchforge.matchmaking.domain.QueueEntryStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

	boolean existsByPlayerIdAndStatus(UUID playerId, QueueEntryStatus status);

	Optional<QueueEntry> findByPlayerIdAndStatus(UUID playerId, QueueEntryStatus status);

	Optional<QueueEntry> findFirstByPlayerIdOrderByCreatedAtDesc(UUID playerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT q FROM QueueEntry q WHERE q.id = :id")
	Optional<QueueEntry> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(
			"""
			SELECT q FROM QueueEntry q
			WHERE q.status = com.matchforge.matchmaking.domain.QueueEntryStatus.QUEUED
			AND q.playerId <> :playerId
			ORDER BY q.createdAt ASC
			""")
	List<QueueEntry> findQueuedOpponentsForUpdate(@Param("playerId") UUID playerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(
			"""
			SELECT q FROM QueueEntry q
			WHERE q.playerId = :playerId AND q.status = com.matchforge.matchmaking.domain.QueueEntryStatus.QUEUED
			""")
	Optional<QueueEntry> findActiveQueuedEntryForUpdate(@Param("playerId") UUID playerId);

	@Query(
			"""
			SELECT q.id FROM QueueEntry q
			WHERE q.status = com.matchforge.matchmaking.domain.QueueEntryStatus.QUEUED
			ORDER BY q.createdAt ASC
			""")
	List<UUID> findQueuedEntryIds();
}