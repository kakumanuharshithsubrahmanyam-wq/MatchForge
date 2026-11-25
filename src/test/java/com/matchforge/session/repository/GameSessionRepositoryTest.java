package com.matchforge.session.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.matchforge.common.config.JpaAuditingConfig;
import com.matchforge.session.domain.GameSession;
import com.matchforge.session.domain.SessionStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.NONE)
class GameSessionRepositoryTest {

	@Autowired
	private GameSessionRepository gameSessionRepository;

	@Test
	void findByMatchId_returnsPersistedSession() {
		UUID matchId = UUID.randomUUID();
		GameSession session = new GameSession();
		session.setMatchId(matchId);
		session.setStatus(SessionStatus.WAITING_FOR_PLAYERS);
		gameSessionRepository.saveAndFlush(session);

		assertThat(gameSessionRepository.findByMatchId(matchId)).isPresent();
	}

	@Test
	void duplicateMatchIdViolatesUniqueConstraint() {
		UUID matchId = UUID.randomUUID();
		gameSessionRepository.saveAndFlush(newSession(matchId));

		assertThatThrownBy(() -> gameSessionRepository.saveAndFlush(newSession(matchId)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private GameSession newSession(UUID matchId) {
		GameSession session = new GameSession();
		session.setMatchId(matchId);
		session.setStatus(SessionStatus.WAITING_FOR_PLAYERS);
		return session;
	}
}