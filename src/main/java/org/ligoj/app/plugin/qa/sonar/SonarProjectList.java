/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.ligoj.bootstrap.core.IDescribableBean;
import org.ligoj.bootstrap.core.NamedBean;

import java.util.List;
import java.util.Map;

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
