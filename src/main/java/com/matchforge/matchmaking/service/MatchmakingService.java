package com.matchforge.matchmaking.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.matchforge.common.error.ConflictException;
import com.matchforge.common.error.ResourceNotFoundException;
import com.matchforge.matchmaking.config.MatchmakingProperties;
import com.matchforge.matchmaking.domain.MatchRecord;
import com.matchforge.matchmaking.domain.MatchStatus;
import com.matchforge.matchmaking.domain.QueueEntry;
import com.matchforge.matchmaking.domain.QueueEntryStatus;
import com.matchforge.matchmaking.dto.MatchResponse;
import com.matchforge.matchmaking.dto.QueueState;
import com.matchforge.matchmaking.dto.QueueStatusResponse;
import com.matchforge.matchmaking.repository.MatchRecordRepository;
import com.matchforge.matchmaking.repository.QueueEntryRepository;
import com.matchforge.common.events.MatchCreatedEvent;
import com.matchforge.common.observability.MatchForgeMetrics;
import com.matchforge.player.dto.PlayerMatchmakingSnapshot;
import com.matchforge.player.service.PlayerService;
import com.matchforge.session.service.SessionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchmakingService {

	private static final Logger log = LoggerFactory.getLogger(MatchmakingService.class);

	private final PlayerService playerService;
	private final SessionService sessionService;
	private final QueueEntryRepository queueEntryRepository;
	private final MatchRecordRepository matchRecordRepository;
	private final MatchmakingAlgorithm matchmakingAlgorithm;
	private final MatchmakingProperties matchmakingProperties;
	private final Clock clock;
	private final ApplicationEventPublisher applicationEventPublisher;
	private final MatchForgeMetrics metrics;

	public MatchmakingService(
			PlayerService playerService,
			SessionService sessionService,
			QueueEntryRepository queueEntryRepository,
			MatchRecordRepository matchRecordRepository,
			MatchmakingProperties matchmakingProperties,
			Clock clock,
			ApplicationEventPublisher applicationEventPublisher,
			MatchForgeMetrics metrics) {
		this.playerService = playerService;
		this.sessionService = sessionService;
		this.queueEntryRepository = queueEntryRepository;
		this.matchRecordRepository = matchRecordRepository;
		this.matchmakingAlgorithm = new MatchmakingAlgorithm();
		this.matchmakingProperties = matchmakingProperties;
		this.clock = clock;
		this.applicationEventPublisher = applicationEventPublisher;
		this.metrics = metrics;
	}

	@Transactional
	public QueueStatusResponse joinQueue(UUID playerId) {
		PlayerMatchmakingSnapshot snapshot = playerService.requireAvailableForQueue(playerId);

		if (queueEntryRepository.existsByPlayerIdAndStatus(playerId, QueueEntryStatus.QUEUED)) {
			throw new ConflictException("Player already has an active queue entry");
		}

		QueueEntry entry = new QueueEntry();
		entry.setPlayerId(playerId);
		entry.setSkillRatingAtQueue(snapshot.skillRating());
		entry.setStatus(QueueEntryStatus.QUEUED);

		try {
			entry = queueEntryRepository.saveAndFlush(entry);
		} catch (DataIntegrityViolationException ex) {
			throw new ConflictException("Player already has an active queue entry");
		}

		playerService.markInQueue(playerId);
		metrics.queueJoined();
		log.info("Player entered matchmaking queue playerId={}", playerId);

		tryFormMatch(entry.getId());
		return resolveQueueStatus(playerId);
	}

	@Transactional
	public QueueStatusResponse leaveQueue(UUID playerId) {
		QueueEntry entry = queueEntryRepository
				.findActiveQueuedEntryForUpdate(playerId)
				.orElseThrow(() -> new ConflictException("Player is not in an active queue entry"));

		entry.setStatus(QueueEntryStatus.CANCELLED);
		queueEntryRepository.save(entry);
		playerService.markAvailableFromQueue(playerId);

		return new QueueStatusResponse(
				playerId, QueueState.CANCELLED, entry.getId(), null, entry.getSkillRatingAtQueue(), entry.getCreatedAt());
	}

	@Transactional(readOnly = true)
	public QueueStatusResponse getQueueStatus(UUID playerId) {
		playerService.getMatchmakingSnapshot(playerId);
		return resolveQueueStatus(playerId);
	}

	@Transactional
	public void processQueue() {
		for (UUID entryId : queueEntryRepository.findQueuedEntryIds()) {
			tryFormMatch(entryId);
		}
	}

	@Transactional(readOnly = true)
	public MatchResponse getMatch(UUID matchId) {
		MatchRecord match = matchRecordRepository
				.findById(matchId)
				.orElseThrow(() -> new ResourceNotFoundException("Match not found"));
		UUID sessionId = sessionService.findSessionIdByMatchId(matchId).sessionId();
		return MatchResponse.from(match, sessionId);
	}

	private QueueStatusResponse resolveQueueStatus(UUID playerId) {
		QueueEntry activeQueued = queueEntryRepository
				.findByPlayerIdAndStatus(playerId, QueueEntryStatus.QUEUED)
				.orElse(null);
		if (activeQueued != null) {
			return new QueueStatusResponse(
					playerId,
					QueueState.QUEUED,
					activeQueued.getId(),
					null,
					activeQueued.getSkillRatingAtQueue(),
					activeQueued.getCreatedAt());
		}

		QueueEntry matchedEntry = queueEntryRepository
				.findByPlayerIdAndStatus(playerId, QueueEntryStatus.MATCHED)
				.orElse(null);
		if (matchedEntry != null) {
			return new QueueStatusResponse(
					playerId,
					QueueState.MATCHED,
					matchedEntry.getId(),
					matchedEntry.getMatchId(),
					matchedEntry.getSkillRatingAtQueue(),
					matchedEntry.getCreatedAt());
		}

		return queueEntryRepository
				.findFirstByPlayerIdOrderByCreatedAtDesc(playerId)
				.filter(entry -> entry.getStatus() == QueueEntryStatus.CANCELLED)
				.map(entry -> new QueueStatusResponse(
						playerId,
						QueueState.CANCELLED,
						entry.getId(),
						null,
						entry.getSkillRatingAtQueue(),
						entry.getCreatedAt()))
				.orElse(new QueueStatusResponse(playerId, QueueState.NOT_QUEUED, null, null, null, null));
	}

	private void tryFormMatch(UUID joiningEntryId) {
		QueueEntry subject = queueEntryRepository
				.findByIdForUpdate(joiningEntryId)
				.orElseThrow(() -> new IllegalStateException("Queue entry not found after join"));
		if (subject.getStatus() != QueueEntryStatus.QUEUED) {
			return;
		}

		List<QueueEntry> opponents = queueEntryRepository.findQueuedOpponentsForUpdate(subject.getPlayerId());
		QueueEntry opponent =
				matchmakingAlgorithm.selectOpponent(subject, opponents, clock.instant(), matchmakingProperties);
		if (opponent == null) {
			return;
		}

		UUID firstLockId = subject.getId().compareTo(opponent.getId()) < 0 ? subject.getId() : opponent.getId();
		UUID secondLockId = subject.getId().compareTo(opponent.getId()) < 0 ? opponent.getId() : subject.getId();

		QueueEntry first = queueEntryRepository.findByIdForUpdate(firstLockId).orElseThrow();
		QueueEntry second = queueEntryRepository.findByIdForUpdate(secondLockId).orElseThrow();

		if (first.getStatus() != QueueEntryStatus.QUEUED || second.getStatus() != QueueEntryStatus.QUEUED) {
			return;
		}

		UUID playerOneId = first.getPlayerId();
		UUID playerTwoId = second.getPlayerId();

		if (playerOneId.equals(playerTwoId)) {
			return;
		}

		if (!matchmakingAlgorithm.isEligible(first, second, clock.instant(), matchmakingProperties)) {
			return;
		}

		MatchRecord match = createMatchRecord(first, second);
		match = matchRecordRepository.save(match);

		first.setStatus(QueueEntryStatus.MATCHED);
		first.setMatchId(match.getId());
		second.setStatus(QueueEntryStatus.MATCHED);
		second.setMatchId(match.getId());
		queueEntryRepository.save(first);
		queueEntryRepository.save(second);

		UUID firstPlayerLock = playerOneId.compareTo(playerTwoId) < 0 ? playerOneId : playerTwoId;
		UUID secondPlayerLock = playerOneId.compareTo(playerTwoId) < 0 ? playerTwoId : playerOneId;
		playerService.markMatched(firstPlayerLock);
		playerService.markMatched(secondPlayerLock);

		sessionService.createSessionForMatch(match.getId(), playerOneId, playerTwoId);
		applicationEventPublisher.publishEvent(new MatchCreatedEvent(match.getId(), clock.instant()));
	}

	private MatchRecord createMatchRecord(QueueEntry first, QueueEntry second) {
		UUID playerOneId = first.getPlayerId().compareTo(second.getPlayerId()) <= 0
				? first.getPlayerId()
				: second.getPlayerId();
		UUID playerTwoId = first.getPlayerId().compareTo(second.getPlayerId()) <= 0
				? second.getPlayerId()
				: first.getPlayerId();

		int playerOneRating =
				first.getPlayerId().equals(playerOneId) ? first.getSkillRatingAtQueue() : second.getSkillRatingAtQueue();
		int playerTwoRating =
				first.getPlayerId().equals(playerTwoId) ? first.getSkillRatingAtQueue() : second.getSkillRatingAtQueue();

		MatchRecord match = new MatchRecord();
		match.setPlayerOneId(playerOneId);
		match.setPlayerTwoId(playerTwoId);
		match.setPlayerOneRating(playerOneRating);
		match.setPlayerTwoRating(playerTwoRating);
		match.setStatus(MatchStatus.PENDING);
		return match;
	}
}