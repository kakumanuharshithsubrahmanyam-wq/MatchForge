package com.matchforge.session.service;

import com.matchforge.common.error.ConflictException;
import com.matchforge.session.domain.ParticipantStatus;
import com.matchforge.session.domain.SessionStatus;

final class SessionLifecycle {

	private SessionLifecycle() {
	}

	static void assertSessionTransition(SessionStatus current, SessionStatus target) {
		if (current == target) {
			return;
		}
		boolean allowed = switch (current) {
			case WAITING_FOR_PLAYERS -> target == SessionStatus.ACTIVE
					|| target == SessionStatus.CANCELLED
					|| target == SessionStatus.EXPIRED;
			case ACTIVE -> target == SessionStatus.PAUSED
					|| target == SessionStatus.COMPLETED;
			case PAUSED -> target == SessionStatus.ACTIVE
					|| target == SessionStatus.COMPLETED
					|| target == SessionStatus.CANCELLED;
			case COMPLETED, CANCELLED, EXPIRED -> false;
		};
		if (!allowed) {
			throw new ConflictException("Invalid session transition from " + current + " to " + target);
		}
	}

	static void assertParticipantTransition(ParticipantStatus current, ParticipantStatus target) {
		if (current == target) {
			return;
		}
		boolean allowed = switch (current) {
			case PENDING -> target == ParticipantStatus.JOINED || target == ParticipantStatus.LEFT;
			case JOINED -> target == ParticipantStatus.DISCONNECTED || target == ParticipantStatus.LEFT;
			case DISCONNECTED -> target == ParticipantStatus.JOINED || target == ParticipantStatus.LEFT;
			case LEFT -> false;
		};
		if (!allowed) {
			throw new ConflictException("Invalid participant transition from " + current + " to " + target);
		}
	}

	static boolean isTerminal(SessionStatus status) {
		return status == SessionStatus.COMPLETED
				|| status == SessionStatus.CANCELLED
				|| status == SessionStatus.EXPIRED;
	}
}