package com.matchforge.session.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class SessionMaintenanceScheduler {

	private static final Logger log = LoggerFactory.getLogger(SessionMaintenanceScheduler.class);

	private final SessionService sessionService;

	public SessionMaintenanceScheduler(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@Scheduled(fixedDelayString = "${matchforge.session.maintenance-interval-ms:5000}")
	public void runMaintenance() {
		int affected = sessionService.processStaleHeartbeats();
		if (affected > 0) {
			log.info("Session maintenance affected {} participant/session records", affected);
		}
	}
}