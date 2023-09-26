/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * SonarQube project retrieved from REST API. Name, and also some additional
 * information.
 * Note: paging is not supported: <code>{ "paging": { "pageIndex": 1, "pageSize": 100, "total": 2 }</code>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SonarProjectList {

	private List<SonarProject> components;

}
