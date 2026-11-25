package com.matchforge.matchmaking.config;

import com.matchforge.matchmaking.service.MatchmakingService;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class MatchmakingQueueScheduler {

	private final MatchmakingService matchmakingService;

	public MatchmakingQueueScheduler(MatchmakingService matchmakingService) {
		this.matchmakingService = matchmakingService;
	}

	@Scheduled(fixedDelayString = "${matchforge.matchmaking.queue-processing-interval-ms:5000}")
	public void processQueue() {
		matchmakingService.processQueue();
	}
}