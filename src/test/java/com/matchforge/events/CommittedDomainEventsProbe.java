package com.matchforge.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.matchforge.common.events.MatchCreatedEvent;
import com.matchforge.common.events.SessionActivatedEvent;
import com.matchforge.common.events.SessionCompletedEvent;
import com.matchforge.common.events.SessionCreatedEvent;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CommittedDomainEventsProbe {

	private final List<MatchCreatedEvent> committedMatches = new CopyOnWriteArrayList<>();
	private final List<SessionCreatedEvent> committedSessions = new CopyOnWriteArrayList<>();
	private final List<SessionActivatedEvent> committedActivations = new CopyOnWriteArrayList<>();
	private final List<SessionCompletedEvent> committedCompletions = new CopyOnWriteArrayList<>();

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onMatchCreated(MatchCreatedEvent event) {
		committedMatches.add(event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSessionCreated(SessionCreatedEvent event) {
		committedSessions.add(event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSessionActivated(SessionActivatedEvent event) {
		committedActivations.add(event);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onSessionCompleted(SessionCompletedEvent event) {
		committedCompletions.add(event);
	}

	public void reset() {
		committedMatches.clear();
		committedSessions.clear();
		committedActivations.clear();
		committedCompletions.clear();
	}

	public List<MatchCreatedEvent> committedMatches() {
		return new ArrayList<>(committedMatches);
	}

	public List<SessionCreatedEvent> committedSessions() {
		return new ArrayList<>(committedSessions);
	}

	public List<SessionActivatedEvent> committedActivations() {
		return new ArrayList<>(committedActivations);
	}

	public List<SessionCompletedEvent> committedCompletions() {
		return new ArrayList<>(committedCompletions);
	}
}