/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * SonarQube branch.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SonarBranch extends SonarProject {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	private String type;
	@JsonProperty("isMain")
	private boolean isMain;
	private String pullRequestKey;
	private String targetBranchName;
	private String analysisDate;
	private Map<String, String> status;
}
