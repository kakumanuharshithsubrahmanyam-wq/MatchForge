package com.matchforge.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.matchforge.common.error.ConflictException;
import com.matchforge.common.error.ResourceNotFoundException;
import com.matchforge.matchmaking.config.MatchmakingProperties;
import com.matchforge.matchmaking.domain.MatchRecord;
import com.matchforge.matchmaking.domain.QueueEntry;
import com.matchforge.matchmaking.domain.QueueEntryStatus;
import com.matchforge.matchmaking.dto.QueueState;
import com.matchforge.matchmaking.repository.MatchRecordRepository;
import com.matchforge.matchmaking.repository.QueueEntryRepository;
import com.matchforge.player.domain.PlayerStatus;
import com.matchforge.player.dto.PlayerMatchmakingSnapshot;
import com.matchforge.common.observability.MatchForgeMetrics;
import com.matchforge.player.service.PlayerService;
import com.matchforge.session.service.SessionService;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

	@Mock
	private PlayerService playerService;

	@Mock
	private SessionService sessionService;

	@Mock
	private QueueEntryRepository queueEntryRepository;

	@Mock
	private MatchRecordRepository matchRecordRepository;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@Mock
	private MatchForgeMetrics metrics;

	@Spy
	private MatchmakingProperties matchmakingProperties = new MatchmakingProperties();

	private Clock clock;

	private MatchmakingService matchmakingService;

	private UUID playerId;
	private UUID opponentPlayerId;

	@BeforeEach
	void setUp() {
		playerId = UUID.randomUUID();
		opponentPlayerId = UUID.randomUUID();
		clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
		matchmakingService = new MatchmakingService(
				playerService,
				sessionService,
				queueEntryRepository,
				matchRecordRepository,
				matchmakingProperties,
				clock,
				applicationEventPublisher,
				metrics);
	}

	@Test
	void joinQueue_duplicateActiveEntry_throwsConflict() {
		when(playerService.requireAvailableForQueue(playerId))
				.thenReturn(new PlayerMatchmakingSnapshot(playerId, 1000, PlayerStatus.AVAILABLE));
		when(queueEntryRepository.existsByPlayerIdAndStatus(playerId, QueueEntryStatus.QUEUED))
				.thenReturn(true);

		assertThatThrownBy(() -> matchmakingService.joinQueue(playerId)).isInstanceOf(ConflictException.class);
	}

	@Test
	void leaveQueue_whenNotQueued_throwsConflict() {
		when(queueEntryRepository.findActiveQueuedEntryForUpdate(playerId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> matchmakingService.leaveQueue(playerId)).isInstanceOf(ConflictException.class);
	}

	@Test
	void getMatch_missingMatch_throwsNotFound() {
		UUID matchId = UUID.randomUUID();
		when(matchRecordRepository.findById(matchId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> matchmakingService.getMatch(matchId)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void getQueueStatus_notQueued_returnsState() {
		when(playerService.getMatchmakingSnapshot(playerId))
				.thenReturn(new PlayerMatchmakingSnapshot(playerId, 1000, PlayerStatus.AVAILABLE));
		when(queueEntryRepository.findByPlayerIdAndStatus(playerId, QueueEntryStatus.QUEUED))
				.thenReturn(Optional.empty());
		when(queueEntryRepository.findByPlayerIdAndStatus(playerId, QueueEntryStatus.MATCHED))
				.thenReturn(Optional.empty());
		when(queueEntryRepository.findFirstByPlayerIdOrderByCreatedAtDesc(playerId)).thenReturn(Optional.empty());

		var status = matchmakingService.getQueueStatus(playerId);
		assertThat(status.state()).isEqualTo(QueueState.NOT_QUEUED);
	}

	@Test
	void joinQueue_createsMatchWhenOpponentExists() {
		when(playerService.requireAvailableForQueue(playerId))
				.thenReturn(new PlayerMatchmakingSnapshot(playerId, 1000, PlayerStatus.AVAILABLE));
		when(queueEntryRepository.existsByPlayerIdAndStatus(playerId, QueueEntryStatus.QUEUED))
				.thenReturn(false);

		UUID subjectEntryId = UUID.randomUUID();
		UUID opponentEntryId = UUID.randomUUID();
		QueueEntry subject =
				queuedEntry(playerId, 1000, subjectEntryId, Instant.parse("2026-01-01T11:59:00Z"));
		QueueEntry opponent = queuedEntry(
				opponentPlayerId, 1000, opponentEntryId, Instant.parse("2026-01-01T11:58:00Z"));

		UUID matchId = UUID.randomUUID();
		QueueEntry matchedSubject = queuedEntry(playerId, 1000, subjectEntryId, subject.getCreatedAt());
		matchedSubject.setStatus(QueueEntryStatus.MATCHED);
		matchedSubject.setMatchId(matchId);

		when(queueEntryRepository.saveAndFlush(any(QueueEntry.class))).thenAnswer(invocation -> {
			QueueEntry entry = invocation.getArgument(0);
			ReflectionTestUtils.setField(entry, "id", subjectEntryId);
			ReflectionTestUtils.setField(entry, "createdAt", subject.getCreatedAt());
			return entry;
		});

		when(queueEntryRepository.findByIdForUpdate(subjectEntryId)).thenReturn(Optional.of(subject));
		when(queueEntryRepository.findQueuedOpponentsForUpdate(playerId)).thenReturn(List.of(opponent));
		when(queueEntryRepository.findByIdForUpdate(
						subjectEntryId.compareTo(opponentEntryId) < 0 ? subjectEntryId : opponentEntryId))
				.thenReturn(Optional.of(subjectEntryId.compareTo(opponentEntryId) < 0 ? subject : opponent));
		when(queueEntryRepository.findByIdForUpdate(
						subjectEntryId.compareTo(opponentEntryId) < 0 ? opponentEntryId : subjectEntryId))
				.thenReturn(Optional.of(subjectEntryId.compareTo(opponentEntryId) < 0 ? opponent : subject));
		when(queueEntryRepository.save(any(QueueEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(matchRecordRepository.save(any(MatchRecord.class))).thenAnswer(invocation -> {
			MatchRecord match = invocation.getArgument(0);
			ReflectionTestUtils.setField(match, "id", matchId);
			ReflectionTestUtils.setField(match, "createdAt", Instant.now());
			return match;
		});
		when(queueEntryRepository.findByPlayerIdAndStatus(playerId, QueueEntryStatus.QUEUED))
				.thenReturn(Optional.empty());
		when(queueEntryRepository.findByPlayerIdAndStatus(playerId, QueueEntryStatus.MATCHED))
				.thenReturn(Optional.of(matchedSubject));

		matchmakingService.joinQueue(playerId);

		verify(playerService).markInQueue(playerId);
		verify(playerService).markMatched(playerId);
		verify(playerService).markMatched(opponentPlayerId);
		verify(sessionService)
				.createSessionForMatch(
						org.mockito.ArgumentMatchers.eq(matchId),
						org.mockito.ArgumentMatchers.any(UUID.class),
						org.mockito.ArgumentMatchers.any(UUID.class));
	}

	@Test
	void joinQueue_noOpponent_staysQueued() {
		when(playerService.requireAvailableForQueue(playerId))
				.thenReturn(new PlayerMatchmakingSnapshot(playerId, 1000, PlayerStatus.AVAILABLE));
		when(queueEntryRepository.existsByPlayerIdAndStatus(playerId, QueueEntryStatus.QUEUED))
				.thenReturn(false);

		UUID entryId = UUID.randomUUID();
		when(queueEntryRepository.saveAndFlush(any(QueueEntry.class))).thenAnswer(invocation -> {
			QueueEntry entry = invocation.getArgument(0);
			ReflectionTestUtils.setField(entry, "id", entryId);
			ReflectionTestUtils.setField(entry, "createdAt", Instant.parse("2026-01-01T12:00:00Z"));
			return entry;
		});

		QueueEntry subject = queuedEntry(playerId, 1000, entryId, Instant.parse("2026-01-01T12:00:00Z"));
		when(queueEntryRepository.findByIdForUpdate(entryId)).thenReturn(Optional.of(subject));
		when(queueEntryRepository.findQueuedOpponentsForUpdate(playerId)).thenReturn(List.of());
		when(queueEntryRepository.findByPlayerIdAndStatus(playerId, QueueEntryStatus.QUEUED))
				.thenReturn(Optional.of(subject));

		var status = matchmakingService.joinQueue(playerId);

		assertThat(status.state()).isEqualTo(QueueState.QUEUED);
		verify(playerService, never()).markMatched(any(UUID.class));
	}

	private QueueEntry queuedEntry(UUID player, int rating, UUID id, Instant createdAt) {
		QueueEntry entry = new QueueEntry();
		ReflectionTestUtils.setField(entry, "id", id);
		entry.setPlayerId(player);
		entry.setSkillRatingAtQueue(rating);
		entry.setStatus(QueueEntryStatus.QUEUED);
		ReflectionTestUtils.setField(entry, "createdAt", createdAt);
		return entry;
	}
}