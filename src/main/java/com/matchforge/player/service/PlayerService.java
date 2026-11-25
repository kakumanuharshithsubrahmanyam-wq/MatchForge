package com.matchforge.player.service;

import java.util.List;
import java.util.UUID;

import com.matchforge.common.error.ConflictException;
import com.matchforge.common.error.ResourceNotFoundException;
import com.matchforge.player.domain.Player;
import com.matchforge.player.domain.PlayerStatus;
import com.matchforge.player.dto.CreatePlayerRequest;
import com.matchforge.player.dto.PlayerMatchmakingSnapshot;
import com.matchforge.player.dto.PlayerResponse;
import com.matchforge.player.dto.SetAvailabilityRequest;
import com.matchforge.player.dto.UpdatePlayerRequest;
import com.matchforge.player.repository.PlayerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

	private static final int DEFAULT_SKILL_RATING = 1000;

	private final PlayerRepository playerRepository;

	public PlayerService(PlayerRepository playerRepository) {
		this.playerRepository = playerRepository;
	}

	@Transactional
	public PlayerResponse register(CreatePlayerRequest request) {
		String username = UsernameNormalizer.normalize(request.username());
		if (username.isEmpty()) {
			throw new IllegalArgumentException("Username is required");
		}
		if (playerRepository.existsByUsername(username)) {
			throw new ConflictException("Username already taken: " + username);
		}

		Player player = new Player();
		player.setUsername(username);
		player.setSkillRating(request.skillRating() != null ? request.skillRating() : DEFAULT_SKILL_RATING);
		player.setStatus(PlayerStatus.OFFLINE);

		return PlayerResponse.from(playerRepository.save(player));
	}

	@Transactional(readOnly = true)
	public PlayerResponse getById(UUID playerId) {
		Player player =
				playerRepository.findById(playerId).orElseThrow(() -> new ResourceNotFoundException("Player not found"));
		return PlayerResponse.from(player);
	}

	@Transactional(readOnly = true)
	public List<PlayerResponse> listPlayers() {
		return playerRepository.findAll().stream().map(PlayerResponse::from).toList();
	}

	@Transactional
	public PlayerResponse updateProfile(UUID playerId, UpdatePlayerRequest request) {
		Player player =
				playerRepository.findById(playerId).orElseThrow(() -> new ResourceNotFoundException("Player not found"));

		if (request.skillRating() != null) {
			player.setSkillRating(request.skillRating());
		}

		return PlayerResponse.from(playerRepository.save(player));
	}

	@Transactional
	public PlayerResponse setAvailability(UUID playerId, SetAvailabilityRequest request) {
		Player player =
				playerRepository.findById(playerId).orElseThrow(() -> new ResourceNotFoundException("Player not found"));

		PlayerStatus current = player.getStatus();
		if (current == PlayerStatus.IN_QUEUE
				|| current == PlayerStatus.MATCHED
				|| current == PlayerStatus.IN_SESSION) {
			throw new ConflictException("Cannot change availability while status is " + current);
		}

		PlayerStatus target = request.available() ? PlayerStatus.AVAILABLE : PlayerStatus.OFFLINE;
		if (current != target) {
			if (current != PlayerStatus.OFFLINE && current != PlayerStatus.AVAILABLE) {
				throw new ConflictException("Invalid status transition from " + current + " to " + target);
			}
			player.setStatus(target);
			player = playerRepository.save(player);
		}

		return PlayerResponse.from(player);
	}

	@Transactional(readOnly = true)
	public PlayerMatchmakingSnapshot getMatchmakingSnapshot(UUID playerId) {
		Player player =
				playerRepository.findById(playerId).orElseThrow(() -> new ResourceNotFoundException("Player not found"));
		return new PlayerMatchmakingSnapshot(player.getId(), player.getSkillRating(), player.getStatus());
	}

	@Transactional
	public PlayerMatchmakingSnapshot requireAvailableForQueue(UUID playerId) {
		Player player = playerRepository
				.findByIdForUpdate(playerId)
				.orElseThrow(() -> new ResourceNotFoundException("Player not found"));
		if (player.getStatus() != PlayerStatus.AVAILABLE) {
			throw new ConflictException("Player must be AVAILABLE to join the queue");
		}
		return new PlayerMatchmakingSnapshot(player.getId(), player.getSkillRating(), player.getStatus());
	}

	@Transactional
	public void markInQueue(UUID playerId) {
		Player player = playerRepository
				.findByIdForUpdate(playerId)
				.orElseThrow(() -> new ResourceNotFoundException("Player not found"));
		if (player.getStatus() != PlayerStatus.AVAILABLE) {
			throw new ConflictException("Player must be AVAILABLE to enter the queue");
		}
		player.setStatus(PlayerStatus.IN_QUEUE);
		playerRepository.save(player);
	}

	@Transactional
	public void markAvailableFromQueue(UUID playerId) {
		Player player = playerRepository
				.findByIdForUpdate(playerId)
				.orElseThrow(() -> new ResourceNotFoundException("Player not found"));
		if (player.getStatus() != PlayerStatus.IN_QUEUE) {
			throw new ConflictException("Player must be IN_QUEUE to leave the queue");
		}
		player.setStatus(PlayerStatus.AVAILABLE);
		playerRepository.save(player);
	}

	@Transactional
	public void markMatched(UUID playerId) {
		Player player = playerRepository
				.findByIdForUpdate(playerId)
				.orElseThrow(() -> new ResourceNotFoundException("Player not found"));
		if (player.getStatus() != PlayerStatus.IN_QUEUE) {
			throw new ConflictException("Player must be IN_QUEUE to be matched");
		}
		player.setStatus(PlayerStatus.MATCHED);
		playerRepository.save(player);
	}

	@Transactional
	public void markInSession(UUID playerId) {
		Player player = playerRepository
				.findByIdForUpdate(playerId)
				.orElseThrow(() -> new ResourceNotFoundException("Player not found"));
		if (player.getStatus() != PlayerStatus.MATCHED) {
			throw new ConflictException("Player must be MATCHED to join a session");
		}
		player.setStatus(PlayerStatus.IN_SESSION);
		playerRepository.save(player);
	}

	@Transactional
	public void markAvailableFromSession(UUID playerId) {
		Player player = playerRepository
				.findByIdForUpdate(playerId)
				.orElseThrow(() -> new ResourceNotFoundException("Player not found"));
		if (player.getStatus() != PlayerStatus.IN_SESSION) {
			throw new ConflictException("Player must be IN_SESSION to leave session");
		}
		player.setStatus(PlayerStatus.AVAILABLE);
		playerRepository.save(player);
	}

	@Transactional
	public void markAvailableFromMatch(UUID playerId) {
		Player player = playerRepository
				.findByIdForUpdate(playerId)
				.orElseThrow(() -> new ResourceNotFoundException("Player not found"));
		if (player.getStatus() != PlayerStatus.MATCHED && player.getStatus() != PlayerStatus.IN_SESSION) {
			throw new ConflictException("Player must be MATCHED to release from match");
		}
		player.setStatus(PlayerStatus.AVAILABLE);
		playerRepository.save(player);
	}
}