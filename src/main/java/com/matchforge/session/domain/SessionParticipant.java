package com.matchforge.session.domain;

import java.time.Instant;
import java.util.UUID;

import com.matchforge.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
		name = "session_participants",
		uniqueConstraints = @UniqueConstraint(name = "ux_session_participant", columnNames = {"session_id", "player_id"}))
public class SessionParticipant extends BaseEntity {

	@Column(nullable = false, updatable = false)
	private UUID sessionId;

	@Column(nullable = false, updatable = false)
	private UUID playerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ParticipantStatus status = ParticipantStatus.PENDING;

	@Column
	private Instant joinedAt;

	@Column
	private Instant lastHeartbeatAt;

	@Column
	private Instant disconnectedAt;

	@Column
	private Instant leftAt;

	@Version
	private long version;

	public UUID getSessionId() {
		return sessionId;
	}

	public void setSessionId(UUID sessionId) {
		this.sessionId = sessionId;
	}

	public UUID getPlayerId() {
		return playerId;
	}

	public void setPlayerId(UUID playerId) {
		this.playerId = playerId;
	}

	public ParticipantStatus getStatus() {
		return status;
	}

	public void setStatus(ParticipantStatus status) {
		this.status = status;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

	public void setJoinedAt(Instant joinedAt) {
		this.joinedAt = joinedAt;
	}

	public Instant getLastHeartbeatAt() {
		return lastHeartbeatAt;
	}

	public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
		this.lastHeartbeatAt = lastHeartbeatAt;
	}

	public Instant getDisconnectedAt() {
		return disconnectedAt;
	}

	public void setDisconnectedAt(Instant disconnectedAt) {
		this.disconnectedAt = disconnectedAt;
	}

	public Instant getLeftAt() {
		return leftAt;
	}

	public void setLeftAt(Instant leftAt) {
		this.leftAt = leftAt;
	}

	public long getVersion() {
		return version;
	}
}