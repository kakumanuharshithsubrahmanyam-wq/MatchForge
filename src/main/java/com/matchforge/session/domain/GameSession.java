package com.matchforge.session.domain;

import java.time.Instant;
import java.util.UUID;

import com.matchforge.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "game_sessions")
public class GameSession extends BaseEntity {

	@Column(nullable = false, unique = true, updatable = false)
	private UUID matchId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SessionStatus status = SessionStatus.WAITING_FOR_PLAYERS;

	@Column(nullable = false)
	private int maximumParticipants = 2;

	@Column
	private Instant startedAt;

	@Column
	private Instant completedAt;

	@Version
	private long version;

	public UUID getMatchId() {
		return matchId;
	}

	public void setMatchId(UUID matchId) {
		this.matchId = matchId;
	}

	public SessionStatus getStatus() {
		return status;
	}

	public void setStatus(SessionStatus status) {
		this.status = status;
	}

	public int getMaximumParticipants() {
		return maximumParticipants;
	}

	public void setMaximumParticipants(int maximumParticipants) {
		this.maximumParticipants = maximumParticipants;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public long getVersion() {
		return version;
	}
}