/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.sonarqube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A SonarQube project a permission template applies to, listed in the status data of a template-based subscription.
 *
 * @param key              The project key.
 * @param name             The project name.
 * @param lastAnalysisDate The last analysis date as SonarQube returns it, {@code null} when never analyzed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SonarQubeTemplateProject(String key, String name, String lastAnalysisDate) {
}
