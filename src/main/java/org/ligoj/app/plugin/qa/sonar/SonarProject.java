package org.ligoj.app.plugin.qa.sonar;

import java.util.List;
import java.util.Map;

import org.ligoj.bootstrap.core.IDescribableBean;
import org.ligoj.bootstrap.core.NamedBean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * SonarQube project retrieved from REST API. Name, and also some additional information.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SonarProject extends NamedBean<Integer> implements IDescribableBean<Integer> {

	private String description;

	/**
	 * SonarQube raw structure.
	 */
	@JsonProperty("msr")
	private List<SonarMesure> rawMesures;

	/**
	 * Mapped values for easiest traversals.
	 */
	private Map<String, Integer> measures;

	/**
	 * Human readable key
	 */
	private String key;

	@JsonProperty(value = "lname")
	public void setLname(final String lname) {
		this.description = lname;
	}
}
