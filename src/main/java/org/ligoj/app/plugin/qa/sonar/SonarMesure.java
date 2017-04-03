package org.ligoj.app.plugin.qa.sonar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * Sonar measure.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SonarMesure {

	private String key;

	/**
	 * Integer value of this measure.
	 */
	@JsonProperty(value = "val")
	private int value;

}
