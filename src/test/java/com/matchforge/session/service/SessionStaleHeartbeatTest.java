package com.matchforge.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.matchforge.session.config.SessionProperties;
import com.matchforge.session.domain.GameSession;
import com.matchforge.session.domain.ParticipantStatus;
import com.matchforge.session.domain.SessionParticipant;
import com.matchforge.session.domain.SessionStatus;
import com.matchforge.session.repository.GameSessionRepository;
import com.matchforge.session.repository.SessionParticipantRepository;
import com.matchforge.common.observability.MatchForgeMetrics;
import com.matchforge.player.service.PlayerService;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionStaleHeartbeatTest {

	@Mock
	private GameSessionRepository gameSessionRepository;

	@Mock
	private SessionParticipantRepository participantRepository;

	@Mock
	private PlayerService playerService;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@Mock
	private MatchForgeMetrics metrics;

	private SessionProperties properties;
	private SessionService sessionService;

	@BeforeEach
	void setUp() {
		properties = new SessionProperties();
		properties.setHeartbeatTimeoutSeconds(30);
		Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:05:00Z"), ZoneOffset.UTC);
		sessionService = new SessionService(
				gameSessionRepository,
				participantRepository,
				playerService,
				properties,
				clock,
				applicationEventPublisher,
				metrics);
	}

	@Test
	void marksStaleParticipantDisconnectedAndPausesSession() {
		UUID sessionId = UUID.randomUUID();
		UUID playerId = UUID.randomUUID();

		GameSession session = new GameSession();
		ReflectionTestUtils.setField(session, "id", sessionId);
		session.setStatus(SessionStatus.ACTIVE);

		SessionParticipant participant = new SessionParticipant();
		participant.setSessionId(sessionId);
		participant.setPlayerId(playerId);
		participant.setStatus(ParticipantStatus.JOINED);
		participant.setLastHeartbeatAt(Instant.parse("2026-01-01T12:04:00Z"));

		when(gameSessionRepository.findSessionsForHeartbeatMaintenance()).thenReturn(java.util.List.of(session));
		when(gameSessionRepository.findByIdForUpdate(sessionId)).thenReturn(java.util.Optional.of(session));
		when(participantRepository.findBySessionId(sessionId)).thenReturn(java.util.List.of(participant));
		when(gameSessionRepository.findAll()).thenReturn(java.util.List.of());
		when(participantRepository.save(any(SessionParticipant.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

		int affected = sessionService.processStaleHeartbeats();

		assertThat(affected).isEqualTo(1);
		assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.DISCONNECTED);
		assertThat(session.getStatus()).isEqualTo(SessionStatus.PAUSED);
		verify(participantRepository).save(participant);
		verify(gameSessionRepository).save(session);
	}
}