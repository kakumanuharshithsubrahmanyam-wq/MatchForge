package com.matchforge.matchmaking.domain;

import java.util.UUID;

import com.matchforge.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "queue_entries")
public class QueueEntry extends BaseEntity {

	@Column(nullable = false, updatable = false)
	private UUID playerId;

	@Column(nullable = false, updatable = false)
	private int skillRatingAtQueue;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private QueueEntryStatus status = QueueEntryStatus.QUEUED;

	@Column
	private UUID matchId;

	@Version
	private long version;

	public UUID getPlayerId() {
		return playerId;
	}

	public void setPlayerId(UUID playerId) {
		this.playerId = playerId;
	}

	public int getSkillRatingAtQueue() {
		return skillRatingAtQueue;
	}

	public void setSkillRatingAtQueue(int skillRatingAtQueue) {
		this.skillRatingAtQueue = skillRatingAtQueue;
	}

	public QueueEntryStatus getStatus() {
		return status;
	}

	public void setStatus(QueueEntryStatus status) {
		this.status = status;
	}

	public UUID getMatchId() {
		return matchId;
	}

	public void setMatchId(UUID matchId) {
		this.matchId = matchId;
	}

	public long getVersion() {
		return version;
	}
}