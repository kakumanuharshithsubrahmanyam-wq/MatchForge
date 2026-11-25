package com.matchforge.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "matchforge.security")
public class SecurityProperties {

	private String jwtIssuer = "matchforge";
	private long jwtExpirationSeconds = 3600;
	private String jwtSecret = "";
	private String adminUsername = "";
	private String adminPassword = "";
	private int maxFailedLoginAttempts = 5;
	private long loginLockoutSeconds = 900;

	public String getJwtIssuer() {
		return jwtIssuer;
	}

	public void setJwtIssuer(String jwtIssuer) {
		this.jwtIssuer = jwtIssuer;
	}

	public long getJwtExpirationSeconds() {
		return jwtExpirationSeconds;
	}

	public void setJwtExpirationSeconds(long jwtExpirationSeconds) {
		this.jwtExpirationSeconds = jwtExpirationSeconds;
	}

	public String getJwtSecret() {
		return jwtSecret;
	}

	public void setJwtSecret(String jwtSecret) {
		this.jwtSecret = jwtSecret;
	}

	public String getAdminUsername() {
		return adminUsername;
	}

	public void setAdminUsername(String adminUsername) {
		this.adminUsername = adminUsername;
	}

	public String getAdminPassword() {
		return adminPassword;
	}

	public void setAdminPassword(String adminPassword) {
		this.adminPassword = adminPassword;
	}

	public int getMaxFailedLoginAttempts() {
		return maxFailedLoginAttempts;
	}

	public void setMaxFailedLoginAttempts(int maxFailedLoginAttempts) {
		this.maxFailedLoginAttempts = maxFailedLoginAttempts;
	}

	public long getLoginLockoutSeconds() {
		return loginLockoutSeconds;
	}

	public void setLoginLockoutSeconds(long loginLockoutSeconds) {
		this.loginLockoutSeconds = loginLockoutSeconds;
	}
}