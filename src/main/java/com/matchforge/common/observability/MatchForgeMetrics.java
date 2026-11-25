package com.matchforge.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public class MatchForgeMetrics {

	private final Counter playerRegistrations;
	private final Counter queueJoins;
	private final Counter matchesCreated;
	private final Counter sessionsCreated;
	private final Counter sessionsCompleted;
	private final Counter sessionsCancelled;
	private final Counter authFailures;
	private final Counter disconnections;

	public MatchForgeMetrics(MeterRegistry meterRegistry) {
		playerRegistrations = Counter.builder("matchforge.player.registrations").register(meterRegistry);
		queueJoins = Counter.builder("matchforge.matchmaking.queue.joins").register(meterRegistry);
		matchesCreated = Counter.builder("matchforge.matchmaking.matches").register(meterRegistry);
		sessionsCreated = Counter.builder("matchforge.sessions.created").register(meterRegistry);
		sessionsCompleted = Counter.builder("matchforge.sessions.completed").register(meterRegistry);
		sessionsCancelled = Counter.builder("matchforge.sessions.cancelled").register(meterRegistry);
		authFailures = Counter.builder("matchforge.auth.failures").register(meterRegistry);
		disconnections = Counter.builder("matchforge.sessions.disconnections").register(meterRegistry);
	}

	public void playerRegistered() {
		playerRegistrations.increment();
	}

	public void queueJoined() {
		queueJoins.increment();
	}

	public void matchCreated() {
		matchesCreated.increment();
	}

	public void sessionCreated() {
		sessionsCreated.increment();
	}

	public void sessionCompleted() {
		sessionsCompleted.increment();
	}

	public void sessionCancelled() {
		sessionsCancelled.increment();
	}

	public void authFailure() {
		authFailures.increment();
	}

	public void participantDisconnected() {
		disconnections.increment();
	}
}