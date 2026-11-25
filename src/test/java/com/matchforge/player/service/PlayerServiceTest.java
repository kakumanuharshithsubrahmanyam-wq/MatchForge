package com.matchforge.player.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.matchforge.common.error.ConflictException;
import com.matchforge.common.error.ResourceNotFoundException;
import com.matchforge.player.domain.Player;
import com.matchforge.player.domain.PlayerStatus;
import com.matchforge.player.dto.CreatePlayerRequest;
import com.matchforge.player.dto.SetAvailabilityRequest;
import com.matchforge.player.repository.PlayerRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

	@Mock
	private PlayerRepository playerRepository;

	@InjectMocks
	private PlayerService playerService;

	@Test
	void register_persistsNormalizedUsernameAndDefaultRating() {
		when(playerRepository.existsByUsername("alpha_one")).thenReturn(false);
		when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
			Player player = invocation.getArgument(0);
			ReflectionTestUtils.setField(player, "id", UUID.randomUUID());
			ReflectionTestUtils.setField(player, "createdAt", Instant.now());
			ReflectionTestUtils.setField(player, "updatedAt", Instant.now());
			return player;
		});

		var response = playerService.register(new CreatePlayerRequest("  Alpha_One  ", null));

		assertThat(response.username()).isEqualTo("alpha_one");
		assertThat(response.skillRating()).isEqualTo(1000);
		assertThat(response.status()).isEqualTo(PlayerStatus.OFFLINE);

		ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
		verify(playerRepository).save(captor.capture());
		assertThat(captor.getValue().getUsername()).isEqualTo("alpha_one");
	}

	@Test
	void register_duplicateUsername_throwsConflict() {
		when(playerRepository.existsByUsername("taken")).thenReturn(true);

		assertThatThrownBy(() -> playerService.register(new CreatePlayerRequest("taken", null)))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("Username already taken");
	}

	@Test
	void getById_missingPlayer_throwsNotFound() {
		UUID id = UUID.randomUUID();
		when(playerRepository.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> playerService.getById(id))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void setAvailability_offlineToAvailable_succeeds() {
		UUID id = UUID.randomUUID();
		Player player = new Player();
		player.setUsername("p1");
		player.setStatus(PlayerStatus.OFFLINE);
		when(playerRepository.findById(id)).thenReturn(Optional.of(player));
		when(playerRepository.save(player)).thenReturn(player);

		var response = playerService.setAvailability(id, new SetAvailabilityRequest(true));

		assertThat(response.status()).isEqualTo(PlayerStatus.AVAILABLE);
	}

	@Test
	void setAvailability_inQueue_throwsConflict() {
		UUID id = UUID.randomUUID();
		Player player = new Player();
		player.setUsername("p1");
		player.setStatus(PlayerStatus.IN_QUEUE);
		when(playerRepository.findById(id)).thenReturn(Optional.of(player));

		assertThatThrownBy(() -> playerService.setAvailability(id, new SetAvailabilityRequest(true)))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("IN_QUEUE");
	}
}