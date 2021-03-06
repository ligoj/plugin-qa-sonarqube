/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import java.io.Serializable;

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
public class SonarMesure implements Serializable {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	private String key;

	/**
	 * Integer value of this measure.
	 */
	@JsonProperty(value = "val")
	private int value;

}
