package com.matchforge.matchmaking.domain;

import java.util.UUID;

import com.matchforge.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_matches")
public class MatchRecord extends BaseEntity {

	@Column(nullable = false, updatable = false)
	private UUID playerOneId;

	@Column(nullable = false, updatable = false)
	private UUID playerTwoId;

	@Column(nullable = false, updatable = false)
	private int playerOneRating;

	@Column(nullable = false, updatable = false)
	private int playerTwoRating;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MatchStatus status = MatchStatus.PENDING;

	public UUID getPlayerOneId() {
		return playerOneId;
	}

	public void setPlayerOneId(UUID playerOneId) {
		this.playerOneId = playerOneId;
	}

	public UUID getPlayerTwoId() {
		return playerTwoId;
	}

	public void setPlayerTwoId(UUID playerTwoId) {
		this.playerTwoId = playerTwoId;
	}

	public int getPlayerOneRating() {
		return playerOneRating;
	}

	public void setPlayerOneRating(int playerOneRating) {
		this.playerOneRating = playerOneRating;
	}

	public int getPlayerTwoRating() {
		return playerTwoRating;
	}

	public void setPlayerTwoRating(int playerTwoRating) {
		this.playerTwoRating = playerTwoRating;
	}

	public MatchStatus getStatus() {
		return status;
	}

	public void setStatus(MatchStatus status) {
		this.status = status;
	}
}