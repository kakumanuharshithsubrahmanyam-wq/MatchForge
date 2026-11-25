package com.matchforge.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "matchforge.api")
public class ApiExposureProperties {

	private boolean swaggerUiEnabled = true;
	private boolean openApiEnabled = true;

	public boolean isSwaggerUiEnabled() {
		return swaggerUiEnabled;
	}

	public void setSwaggerUiEnabled(boolean swaggerUiEnabled) {
		this.swaggerUiEnabled = swaggerUiEnabled;
	}

	public boolean isOpenApiEnabled() {
		return openApiEnabled;
	}

	public void setOpenApiEnabled(boolean openApiEnabled) {
		this.openApiEnabled = openApiEnabled;
	}
}