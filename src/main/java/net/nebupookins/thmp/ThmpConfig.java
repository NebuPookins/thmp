package net.nebupookins.thmp;

import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.Configuration;

public class ThmpConfig extends Configuration {
	@NotEmpty
	private String freemarkerTemplateDir;
	
	private boolean devMode;
	
	@JsonProperty
	public String getFreemarkerTemplateDir() {
		return freemarkerTemplateDir;
	}
	
	@JsonProperty
	public void setFreemarkerTemplateDir(String freemarkerTemplateDir) {
		this.freemarkerTemplateDir = freemarkerTemplateDir;
	}

	@JsonProperty
	public boolean isDevMode() {
		return devMode;
	}

	@JsonProperty
	public void setDevMode(boolean devMode) {
		this.devMode = devMode;
	}
}
