package com.matchforge.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.matchforge.common.events.SessionActivatedEvent;
import com.matchforge.common.events.SessionCompletedEvent;
import com.matchforge.support.AuthTestHelper;
import com.matchforge.support.AuthTestHelper.RegisteredPlayer;
import com.matchforge.support.IntegrationTestDatabaseCleaner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(CommittedDomainEventsProbe.class)
class DomainEventIntegrationTest {

	@Autowired
	private CommittedDomainEventsProbe committedEvents;

	@Autowired
	private com.matchforge.matchmaking.service.MatchmakingService matchmakingService;

	@Autowired
	private com.matchforge.player.service.PlayerService playerService;

	@Autowired
	private com.matchforge.auth.repository.AccountRepository accountRepository;

	@Autowired
	private com.matchforge.player.repository.PlayerRepository playerRepository;

	@Autowired
	private com.matchforge.matchmaking.repository.QueueEntryRepository queueEntryRepository;

	@Autowired
	private com.matchforge.matchmaking.repository.MatchRecordRepository matchRecordRepository;

	@Autowired
	private com.matchforge.session.repository.GameSessionRepository gameSessionRepository;

	@Autowired
	private com.matchforge.session.repository.SessionParticipantRepository sessionParticipantRepository;

	@Autowired
	private com.matchforge.session.service.SessionService sessionService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void clean() {
		committedEvents.reset();
		IntegrationTestDatabaseCleaner.resetGameplayData(
				sessionParticipantRepository,
				gameSessionRepository,
				queueEntryRepository,
				matchRecordRepository,
				accountRepository,
				playerRepository);
	}

	@Test
	void successfulMatchPublishesAfterCommit() {
		UUID playerOne = createAvailablePlayer("event_p1");
		UUID playerTwo = createAvailablePlayer("event_p2");
		matchmakingService.joinQueue(playerOne);
		matchmakingService.joinQueue(playerTwo);

		assertThat(committedEvents.committedMatches()).hasSize(1);
		assertThat(committedEvents.committedSessions()).hasSize(1);
	}

	@Test
	void rollbackSuppressesMatchEvent() {
		UUID playerOne = createAvailablePlayer("rollback_p1");
		UUID playerTwo = createAvailablePlayer("rollback_p2");
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.execute(status -> {
			matchmakingService.joinQueue(playerOne);
			matchmakingService.joinQueue(playerTwo);
			status.setRollbackOnly();
			return null;
		});
		assertThat(committedEvents.committedMatches()).isEmpty();
		assertThat(committedEvents.committedSessions()).isEmpty();
	}

	@Test
	void sessionActivationAndCompletionPublishEvents() {
		UUID playerOne = createAvailablePlayer("lifecycle_p1");
		UUID playerTwo = createAvailablePlayer("lifecycle_p2");
		matchmakingService.joinQueue(playerOne);
		matchmakingService.joinQueue(playerTwo);

		var match = matchRecordRepository.findAll().getFirst();
		var sessionId = gameSessionRepository.findByMatchId(match.getId()).orElseThrow().getId();

		sessionService.joinSession(sessionId, playerOne);
		sessionService.joinSession(sessionId, playerTwo);
		assertThat(committedEvents.committedActivations()).hasSize(1);

		sessionService.completeSession(sessionId);
		assertThat(committedEvents.committedCompletions()).hasSize(1);
	}

	private UUID createAvailablePlayer(String username) {
		var player = playerService.register(new com.matchforge.player.dto.CreatePlayerRequest(username, 1000));
		playerService.setAvailability(player.id(), new com.matchforge.player.dto.SetAvailabilityRequest(true));
		return player.id();
	}
}