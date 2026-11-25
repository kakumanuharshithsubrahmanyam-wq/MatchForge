package com.matchforge.player.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.matchforge.common.config.JpaAuditingConfig;
import com.matchforge.player.domain.Player;
import com.matchforge.player.domain.PlayerStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class PlayerRepositoryTest {

	@Autowired
	private PlayerRepository playerRepository;

	@Test
	void save_assignsUuidAndEnforcesUniqueUsername() {
		Player first = new Player();
		first.setUsername("unique_player");
		first.setSkillRating(1000);
		first.setStatus(PlayerStatus.OFFLINE);
		Player saved = playerRepository.saveAndFlush(first);

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getId()).isInstanceOf(UUID.class);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(playerRepository.existsByUsername("unique_player")).isTrue();

		Player duplicate = new Player();
		duplicate.setUsername("unique_player");
		duplicate.setSkillRating(900);
		duplicate.setStatus(PlayerStatus.OFFLINE);

		assertThatThrownBy(() -> playerRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findByUsername_returnsPlayer() {
		Player player = new Player();
		player.setUsername("lookup_user");
		player.setSkillRating(1100);
		player.setStatus(PlayerStatus.AVAILABLE);
		playerRepository.saveAndFlush(player);

		assertThat(playerRepository.findByUsername("lookup_user")).isPresent();
	}
}