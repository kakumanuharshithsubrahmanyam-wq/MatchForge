package com.matchforge.player.domain;

import com.matchforge.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "players")
public class Player extends BaseEntity {

	@Column(nullable = false, unique = true, length = 32)
	private String username;

	@Column(nullable = false)
	private int skillRating = 1000;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private PlayerStatus status = PlayerStatus.OFFLINE;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public int getSkillRating() {
		return skillRating;
	}

	public void setSkillRating(int skillRating) {
		this.skillRating = skillRating;
	}

	public PlayerStatus getStatus() {
		return status;
	}

	public void setStatus(PlayerStatus status) {
		this.status = status;
	}
}