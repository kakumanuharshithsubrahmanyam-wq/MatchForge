package com.matchforge.session.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.matchforge.common.error.ConflictException;
import com.matchforge.common.error.ResourceNotFoundException;
import com.matchforge.common.events.SessionActivatedEvent;
import com.matchforge.common.events.SessionCancelledEvent;
import com.matchforge.common.events.SessionCompletedEvent;
import com.matchforge.common.events.SessionCreatedEvent;
import com.matchforge.common.observability.MatchForgeMetrics;
import com.matchforge.session.config.SessionProperties;
import com.matchforge.session.domain.GameSession;
import com.matchforge.session.domain.ParticipantStatus;
import com.matchforge.session.domain.SessionParticipant;
import com.matchforge.session.domain.SessionStatus;
import com.matchforge.session.dto.SessionParticipantResponse;
import com.matchforge.session.dto.SessionResponse;
import com.matchforge.session.repository.GameSessionRepository;
import com.matchforge.session.repository.SessionParticipantRepository;
import com.matchforge.player.service.PlayerService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

	private final GameSessionRepository gameSessionRepository;
	private final SessionParticipantRepository participantRepository;
	private final PlayerService playerService;
	private final SessionProperties sessionProperties;
	private final Clock clock;
	private final ApplicationEventPublisher applicationEventPublisher;
	private final MatchForgeMetrics metrics;

	public SessionService(
			GameSessionRepository gameSessionRepository,
			SessionParticipantRepository participantRepository,
			PlayerService playerService,
			SessionProperties sessionProperties,
			Clock clock,
			ApplicationEventPublisher applicationEventPublisher,
			MatchForgeMetrics metrics) {
		this.gameSessionRepository = gameSessionRepository;
		this.participantRepository = participantRepository;
		this.playerService = playerService;
		this.sessionProperties = sessionProperties;
		this.clock = clock;
		this.applicationEventPublisher = applicationEventPublisher;
		this.metrics = metrics;
	}

	@Transactional
	public UUID createSessionForMatch(UUID matchId, UUID playerOneId, UUID playerTwoId) {
		if (gameSessionRepository.findByMatchId(matchId).isPresent()) {
			throw new ConflictException("Session already exists for match");
		}

		GameSession session = new GameSession();
		session.setMatchId(matchId);
		session.setStatus(SessionStatus.WAITING_FOR_PLAYERS);
		session.setMaximumParticipants(2);

		try {
			session = gameSessionRepository.saveAndFlush(session);
		} catch (DataIntegrityViolationException ex) {
			throw new ConflictException("Session already exists for match");
		}

		createParticipant(session.getId(), playerOneId);
		createParticipant(session.getId(), playerTwoId);

		applicationEventPublisher.publishEvent(
				new SessionCreatedEvent(session.getId(), matchId, clock.instant()));
		return session.getId();
	}

	@Transactional(readOnly = true)
	public OptionalSessionId findSessionIdByMatchId(UUID matchId) {
		return gameSessionRepository
				.findByMatchId(matchId)
				.map(session -> new OptionalSessionId(session.getId()))
				.orElse(new OptionalSessionId(null));
	}

	@Transactional(readOnly = true)
	public SessionResponse getSession(UUID sessionId) {
		GameSession session = gameSessionRepository
				.findById(sessionId)
				.orElseThrow(() -> new ResourceNotFoundException("Session not found"));
		List<SessionParticipant> participants = participantRepository.findBySessionId(sessionId);
		return SessionResponse.from(session, participants);
	}

	@Transactional(readOnly = true)
	public List<SessionParticipantResponse> listParticipants(UUID sessionId) {
		assertSessionExists(sessionId);
		return participantRepository.findBySessionId(sessionId).stream()
				.map(SessionParticipantResponse::from)
				.toList();
	}

	@Transactional
	public SessionResponse joinSession(UUID sessionId, UUID playerId) {
		GameSession session = lockSession(sessionId);
		if (SessionLifecycle.isTerminal(session.getStatus())) {
			throw new ConflictException("Cannot join terminal session");
		}
		if (session.getStatus() != SessionStatus.WAITING_FOR_PLAYERS
				&& session.getStatus() != SessionStatus.ACTIVE
				&& session.getStatus() != SessionStatus.PAUSED) {
			throw new ConflictException("Session is not joinable");
		}

		SessionParticipant participant = participantRepository
				.findForUpdate(sessionId, playerId)
				.orElseThrow(() -> new ConflictException("Player is not assigned to this session"));

		if (participant.getStatus() == ParticipantStatus.JOINED) {
			return SessionResponse.from(session, participantRepository.findBySessionId(sessionId));
		}
		if (participant.getStatus() != ParticipantStatus.PENDING
				&& participant.getStatus() != ParticipantStatus.DISCONNECTED) {
			throw new ConflictException("Participant cannot join in status " + participant.getStatus());
		}

		Instant now = clock.instant();
		if (participant.getStatus() == ParticipantStatus.PENDING) {
			SessionLifecycle.assertParticipantTransition(participant.getStatus(), ParticipantStatus.JOINED);
			participant.setStatus(ParticipantStatus.JOINED);
			participant.setJoinedAt(now);
			playerService.markInSession(playerId);
		} else if (participant.getStatus() == ParticipantStatus.DISCONNECTED) {
			SessionLifecycle.assertParticipantTransition(participant.getStatus(), ParticipantStatus.JOINED);
			participant.setStatus(ParticipantStatus.JOINED);
			participant.setDisconnectedAt(null);
		}
		participant.setLastHeartbeatAt(now);
		participantRepository.save(participant);

		refreshSessionActivation(session);
		return SessionResponse.from(
				gameSessionRepository.findById(sessionId).orElseThrow(), participantRepository.findBySessionId(sessionId));
	}

	@Transactional
	public SessionResponse heartbeat(UUID sessionId, UUID playerId) {
		GameSession session = lockSession(sessionId);
		if (SessionLifecycle.isTerminal(session.getStatus())) {
			throw new ConflictException("Session is terminal");
		}

		SessionParticipant participant = participantRepository
				.findForUpdate(sessionId, playerId)
				.orElseThrow(() -> new ConflictException("Player is not assigned to this session"));

		if (participant.getStatus() != ParticipantStatus.JOINED) {
			throw new ConflictException("Heartbeat requires JOINED participant");
		}

		participant.setLastHeartbeatAt(clock.instant());
		participantRepository.save(participant);

		return SessionResponse.from(session, participantRepository.findBySessionId(sessionId));
	}

	@Transactional
	public SessionResponse disconnect(UUID sessionId, UUID playerId) {
		GameSession session = lockSession(sessionId);
		if (SessionLifecycle.isTerminal(session.getStatus())) {
			throw new ConflictException("Session is terminal");
		}

		SessionParticipant participant = participantRepository
				.findForUpdate(sessionId, playerId)
				.orElseThrow(() -> new ConflictException("Player is not assigned to this session"));

		if (participant.getStatus() == ParticipantStatus.DISCONNECTED) {
			return SessionResponse.from(session, participantRepository.findBySessionId(sessionId));
		}
		if (participant.getStatus() != ParticipantStatus.JOINED) {
			throw new ConflictException("Only JOINED participants can disconnect");
		}

		Instant now = clock.instant();
		SessionLifecycle.assertParticipantTransition(participant.getStatus(), ParticipantStatus.DISCONNECTED);
		participant.setStatus(ParticipantStatus.DISCONNECTED);
		participant.setDisconnectedAt(now);
		participantRepository.save(participant);
		metrics.participantDisconnected();

		if (session.getStatus() == SessionStatus.ACTIVE) {
			SessionLifecycle.assertSessionTransition(session.getStatus(), SessionStatus.PAUSED);
			session.setStatus(SessionStatus.PAUSED);
			gameSessionRepository.save(session);
		}

		return SessionResponse.from(
				gameSessionRepository.findById(sessionId).orElseThrow(), participantRepository.findBySessionId(sessionId));
	}

	@Transactional
	public SessionResponse reconnect(UUID sessionId, UUID playerId) {
		return joinSession(sessionId, playerId);
	}

	@Transactional
	public SessionResponse completeSession(UUID sessionId) {
		GameSession session = lockSession(sessionId);
		if (SessionLifecycle.isTerminal(session.getStatus())) {
			throw new ConflictException("Session is already terminal");
		}
		if (session.getStatus() != SessionStatus.ACTIVE && session.getStatus() != SessionStatus.PAUSED) {
			throw new ConflictException("Only ACTIVE or PAUSED sessions may be completed");
		}

		Instant now = clock.instant();
		SessionLifecycle.assertSessionTransition(session.getStatus(), SessionStatus.COMPLETED);
		session.setStatus(SessionStatus.COMPLETED);
		session.setCompletedAt(now);
		gameSessionRepository.save(session);

		List<SessionParticipant> participants = participantRepository.findBySessionId(sessionId);
		for (SessionParticipant participant : participants) {
			if (participant.getStatus() != ParticipantStatus.LEFT) {
				participant.setStatus(ParticipantStatus.LEFT);
				participant.setLeftAt(now);
				participantRepository.save(participant);
				if (participant.getJoinedAt() != null) {
					playerService.markAvailableFromSession(participant.getPlayerId());
				} else {
					playerService.markAvailableFromMatch(participant.getPlayerId());
				}
			}
		}

		applicationEventPublisher.publishEvent(new SessionCompletedEvent(sessionId, now));
		return SessionResponse.from(session, participants);
	}

	@Transactional
	public SessionResponse cancelSession(UUID sessionId) {
		GameSession session = lockSession(sessionId);
		if (SessionLifecycle.isTerminal(session.getStatus())) {
			throw new ConflictException("Session is already terminal");
		}
		if (session.getStatus() != SessionStatus.WAITING_FOR_PLAYERS
				&& session.getStatus() != SessionStatus.PAUSED) {
			throw new ConflictException("Session cannot be cancelled from status " + session.getStatus());
		}

		Instant now = clock.instant();
		SessionLifecycle.assertSessionTransition(session.getStatus(), SessionStatus.CANCELLED);
		session.setStatus(SessionStatus.CANCELLED);
		session.setCompletedAt(now);
		gameSessionRepository.save(session);

		List<SessionParticipant> participants = participantRepository.findBySessionId(sessionId);
		for (SessionParticipant participant : participants) {
			participant.setStatus(ParticipantStatus.LEFT);
			participant.setLeftAt(now);
			participantRepository.save(participant);
			if (participant.getJoinedAt() != null) {
				playerService.markAvailableFromSession(participant.getPlayerId());
			} else {
				playerService.markAvailableFromMatch(participant.getPlayerId());
			}
		}

		applicationEventPublisher.publishEvent(new SessionCancelledEvent(sessionId, now));
		return SessionResponse.from(session, participants);
	}

	@Transactional
	public int processStaleHeartbeats() {
		int affected = 0;
		Instant now = clock.instant();
		Instant staleThreshold = now.minusSeconds(sessionProperties.getHeartbeatTimeoutSeconds());

		for (GameSession session : gameSessionRepository.findSessionsForHeartbeatMaintenance()) {
			GameSession locked = lockSession(session.getId());
			if (SessionLifecycle.isTerminal(locked.getStatus())) {
				continue;
			}
			boolean changed = false;
			for (SessionParticipant participant : participantRepository.findBySessionId(locked.getId())) {
				if (participant.getStatus() != ParticipantStatus.JOINED) {
					continue;
				}
				Instant last = participant.getLastHeartbeatAt();
				if (last != null && last.isBefore(staleThreshold)) {
					participant.setStatus(ParticipantStatus.DISCONNECTED);
					participant.setDisconnectedAt(now);
					participantRepository.save(participant);
					changed = true;
					affected++;
				}
			}
			if (changed && locked.getStatus() == SessionStatus.ACTIVE) {
				SessionLifecycle.assertSessionTransition(locked.getStatus(), SessionStatus.PAUSED);
				locked.setStatus(SessionStatus.PAUSED);
				gameSessionRepository.save(locked);
			}
		}

		affected += expireWaitingSessions(now);
		return affected;
	}

	private int expireWaitingSessions(Instant now) {
		int expired = 0;
		for (GameSession session : gameSessionRepository.findAll()) {
			if (session.getStatus() != SessionStatus.WAITING_FOR_PLAYERS) {
				continue;
			}
			Instant created = session.getCreatedAt();
			if (created == null) {
				continue;
			}
			if (Duration.between(created, now).getSeconds() < sessionProperties.getWaitingExpirySeconds()) {
				continue;
			}
			try {
				GameSession locked = lockSession(session.getId());
				if (locked.getStatus() != SessionStatus.WAITING_FOR_PLAYERS) {
					continue;
				}
				SessionLifecycle.assertSessionTransition(locked.getStatus(), SessionStatus.EXPIRED);
				locked.setStatus(SessionStatus.EXPIRED);
				locked.setCompletedAt(now);
				gameSessionRepository.save(locked);
				for (SessionParticipant participant : participantRepository.findBySessionId(locked.getId())) {
					participant.setStatus(ParticipantStatus.LEFT);
					participant.setLeftAt(now);
					participantRepository.save(participant);
					playerService.markAvailableFromMatch(participant.getPlayerId());
				}
				expired++;
			} catch (ConflictException ignored) {
				// raced with join/cancel
			}
		}
		return expired;
	}

	private void refreshSessionActivation(GameSession session) {
		List<SessionParticipant> participants = participantRepository.findBySessionId(session.getId());
		boolean allJoined = participants.stream().allMatch(p -> p.getStatus() == ParticipantStatus.JOINED);
		if (allJoined && session.getStatus() == SessionStatus.WAITING_FOR_PLAYERS) {
			SessionLifecycle.assertSessionTransition(session.getStatus(), SessionStatus.ACTIVE);
			session.setStatus(SessionStatus.ACTIVE);
			session.setStartedAt(clock.instant());
			gameSessionRepository.save(session);
			applicationEventPublisher.publishEvent(new SessionActivatedEvent(session.getId(), clock.instant()));
		} else if (allJoined && session.getStatus() == SessionStatus.PAUSED) {
			SessionLifecycle.assertSessionTransition(session.getStatus(), SessionStatus.ACTIVE);
			session.setStatus(SessionStatus.ACTIVE);
			gameSessionRepository.save(session);
			applicationEventPublisher.publishEvent(new SessionActivatedEvent(session.getId(), clock.instant()));
		}
	}

	private void createParticipant(UUID sessionId, UUID playerId) {
		SessionParticipant participant = new SessionParticipant();
		participant.setSessionId(sessionId);
		participant.setPlayerId(playerId);
		participant.setStatus(ParticipantStatus.PENDING);
		participantRepository.save(participant);
	}

	private GameSession lockSession(UUID sessionId) {
		return gameSessionRepository
				.findByIdForUpdate(sessionId)
				.orElseThrow(() -> new ResourceNotFoundException("Session not found"));
	}

	private void assertSessionExists(UUID sessionId) {
		if (!gameSessionRepository.existsById(sessionId)) {
			throw new ResourceNotFoundException("Session not found");
		}
	}

	public record OptionalSessionId(UUID sessionId) {
	}
}