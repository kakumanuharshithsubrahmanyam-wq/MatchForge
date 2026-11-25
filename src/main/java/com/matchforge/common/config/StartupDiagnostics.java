package com.matchforge.common.config;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Safe startup timing logs for container platforms (no credentials or secrets).
 */
@Component
public class StartupDiagnostics {

	private static final Logger log = LoggerFactory.getLogger(StartupDiagnostics.class);

	private final Instant createdAt = Instant.now();

	@EventListener
	public void onContextRefreshed(ContextRefreshedEvent event) {
		if (event.getApplicationContext().getParent() != null) {
			return;
		}
		log.info(
				"Startup phase: root context refreshed in {} ms",
				Duration.between(createdAt, Instant.now()).toMillis());
	}

	@EventListener
	public void onWebServerInitialized(ServletWebServerInitializedEvent event) {
		int port = event.getWebServer().getPort();
		log.info(
				"Startup phase: HTTP server initialized and listening on port {} (elapsed {} ms)",
				port,
				Duration.between(createdAt, Instant.now()).toMillis());
	}

	@EventListener
	public void onStarted(ApplicationStartedEvent event) {
		log.info(
				"Startup phase: application started event in {} ms",
				Duration.between(createdAt, Instant.now()).toMillis());
	}

	@EventListener
	public void onApplicationReady(ApplicationReadyEvent event) {
		Runtime runtime = Runtime.getRuntime();
		long heapMaxMb = runtime.maxMemory() / (1024 * 1024);
		long heapTotalMb = runtime.totalMemory() / (1024 * 1024);
		log.info(
				"MatchForge startup complete in {} ms (heap max {} MiB, allocated {} MiB)",
				Duration.between(createdAt, Instant.now()).toMillis(),
				heapMaxMb,
				heapTotalMb);
	}
}