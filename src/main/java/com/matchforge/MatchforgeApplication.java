package com.matchforge;

import com.matchforge.common.config.ProductionDatabaseEnvironmentValidator;
import com.matchforge.common.config.EarlyStartupLogger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MatchforgeApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(MatchforgeApplication.class);
		application.addListeners(
				new ProductionDatabaseEnvironmentValidator(),
				EarlyStartupLogger.startingListener(),
				EarlyStartupLogger.environmentPreparedListener());
		application.run(args);
	}

}