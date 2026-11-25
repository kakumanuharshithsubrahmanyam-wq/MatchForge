package com.matchforge.matchmaking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "matchforge.matchmaking")
public class MatchmakingProperties {

	private int initialRatingDifference = 100;
	private int ratingExpansion = 50;
	private int expansionIntervalSeconds = 30;
	private int maxRatingDifference = 500;

	public int getInitialRatingDifference() {
		return initialRatingDifference;
	}

	public void setInitialRatingDifference(int initialRatingDifference) {
		this.initialRatingDifference = initialRatingDifference;
	}

	public int getRatingExpansion() {
		return ratingExpansion;
	}

	public void setRatingExpansion(int ratingExpansion) {
		this.ratingExpansion = ratingExpansion;
	}

	public int getExpansionIntervalSeconds() {
		return expansionIntervalSeconds;
	}

	public void setExpansionIntervalSeconds(int expansionIntervalSeconds) {
		this.expansionIntervalSeconds = expansionIntervalSeconds;
	}

	public int getMaxRatingDifference() {
		return maxRatingDifference;
	}

	public void setMaxRatingDifference(int maxRatingDifference) {
		this.maxRatingDifference = maxRatingDifference;
	}
}