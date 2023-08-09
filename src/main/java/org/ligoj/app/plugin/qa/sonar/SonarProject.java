/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.ligoj.bootstrap.core.IDescribableBean;
import org.ligoj.bootstrap.core.NamedBean;

import java.util.List;
import java.util.Map;

/**
 * SonarQube project retrieved from REST API. Name, and also some additional
 * information.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SonarProject extends NamedBean<String> implements IDescribableBean<String> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	private String description;

	/**
	 * SonarQube raw structure.
	 */
	@JsonProperty("msr")
	@JsonAlias("measures")
	@Transient
	private List<SonarMeasure> rawMeasures;

	/**
	 * Mapped values for easiest traversals.
	 */
	private Map<String, Integer> measuresAsMap;

	/**
	 * List of branches.
	 */
	private List<SonarBranch> branches;

	/**
	 * Human-readable key
	 */
	private String key;

	/**
	 * The local name of the project.
	 * 
	 * @param lname The local name of the project.
	 */
	@JsonProperty("lname")
	public void setLname(final String lname) {
		this.description = lname;
	}
}
