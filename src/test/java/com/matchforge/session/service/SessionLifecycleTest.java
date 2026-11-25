package com.matchforge.session.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matchforge.common.error.ConflictException;
import com.matchforge.session.domain.ParticipantStatus;
import com.matchforge.session.domain.SessionStatus;

import org.junit.jupiter.api.Test;

class SessionLifecycleTest {

	@Test
	void allowsExpectedSessionTransitions() {
		assertThatCode(() -> SessionLifecycle.assertSessionTransition(SessionStatus.WAITING_FOR_PLAYERS, SessionStatus.ACTIVE))
				.doesNotThrowAnyException();
		assertThatCode(() -> SessionLifecycle.assertSessionTransition(SessionStatus.ACTIVE, SessionStatus.PAUSED))
				.doesNotThrowAnyException();
		assertThatCode(() -> SessionLifecycle.assertSessionTransition(SessionStatus.PAUSED, SessionStatus.ACTIVE))
				.doesNotThrowAnyException();
	}

	@Test
	void rejectsTerminalSessionReactivation() {
		assertThatThrownBy(() -> SessionLifecycle.assertSessionTransition(SessionStatus.COMPLETED, SessionStatus.ACTIVE))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void rejectsInvalidParticipantTransition() {
		assertThatThrownBy(() ->
						SessionLifecycle.assertParticipantTransition(ParticipantStatus.LEFT, ParticipantStatus.JOINED))
				.isInstanceOf(ConflictException.class);
	}
}