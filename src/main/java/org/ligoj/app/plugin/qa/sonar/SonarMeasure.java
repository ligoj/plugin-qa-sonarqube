/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class SonarMeasure implements Serializable {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	@JsonProperty("key")
	@JsonAlias("metric")
	private String key;

	/**
	 * Integer value of this measure.
	 */
	@JsonProperty("val")
	@JsonAlias("value")
	private double value;

}
