package com.matchforge.common.observability;

import com.matchforge.common.events.MatchCreatedEvent;
import com.matchforge.common.events.SessionActivatedEvent;
import com.matchforge.common.events.SessionCancelledEvent;
import com.matchforge.common.events.SessionCompletedEvent;
import com.matchforge.common.events.SessionCreatedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DomainEventListener {

	private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

	private final MatchForgeMetrics metrics;

	public DomainEventListener(MatchForgeMetrics metrics) {
		this.metrics = metrics;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMatchCreated(MatchCreatedEvent event) {
		metrics.matchCreated();
		log.info("Match created matchId={}", event.matchId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSessionCreated(SessionCreatedEvent event) {
		metrics.sessionCreated();
		log.info("Session created sessionId={} matchId={}", event.sessionId(), event.matchId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSessionActivated(SessionActivatedEvent event) {
		log.info("Session activated sessionId={}", event.sessionId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSessionCompleted(SessionCompletedEvent event) {
		metrics.sessionCompleted();
		log.info("Session completed sessionId={}", event.sessionId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSessionCancelled(SessionCancelledEvent event) {
		metrics.sessionCancelled();
		log.info("Session cancelled sessionId={}", event.sessionId());
	}
}