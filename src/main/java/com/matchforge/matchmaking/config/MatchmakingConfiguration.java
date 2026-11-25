package com.matchforge.matchmaking.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MatchmakingProperties.class)
public class MatchmakingConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}