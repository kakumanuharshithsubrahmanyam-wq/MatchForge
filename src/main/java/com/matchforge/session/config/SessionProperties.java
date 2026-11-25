package com.matchforge.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "matchforge.session")
public class SessionProperties {

	private int heartbeatTimeoutSeconds = 60;
	private int maintenanceIntervalMs = 5000;
	private int waitingExpirySeconds = 3600;

	public int getHeartbeatTimeoutSeconds() {
		return heartbeatTimeoutSeconds;
	}

	public void setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) {
		this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
	}

	public int getMaintenanceIntervalMs() {
		return maintenanceIntervalMs;
	}

	public void setMaintenanceIntervalMs(int maintenanceIntervalMs) {
		this.maintenanceIntervalMs = maintenanceIntervalMs;
	}

	public int getWaitingExpirySeconds() {
		return waitingExpirySeconds;
	}

	public void setWaitingExpirySeconds(int waitingExpirySeconds) {
		this.waitingExpirySeconds = waitingExpirySeconds;
	}
}