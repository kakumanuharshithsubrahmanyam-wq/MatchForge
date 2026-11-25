package com.matchforge.common.config;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;

/**
 * Logs very early startup phases before the application context exists.
 */
public final class EarlyStartupLogger {

	private static final Logger log = LoggerFactory.getLogger(EarlyStartupLogger.class);
	private static final Instant processStart = Instant.now();

	private EarlyStartupLogger() {}

	public static ApplicationListener<ApplicationStartingEvent> startingListener() {
		return event -> log.info("Startup phase: JVM launching");
	}

	public static ApplicationListener<ApplicationEnvironmentPreparedEvent> environmentPreparedListener() {
		return event -> log.info(
				"Startup phase: environment prepared in {} ms",
				Duration.between(processStart, Instant.now()).toMillis());
	}
}