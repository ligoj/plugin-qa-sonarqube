/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.plugin.qa.QaResource;
import org.ligoj.app.plugin.qa.QaServicePlugin;
import org.ligoj.app.resource.NormalizeFormat;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.VersionUtils;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Sonar resource.
 */
@Path(SonarPluginResource.URL)
@Service
@Produces(MediaType.APPLICATION_JSON)
public class SonarPluginResource extends AbstractToolPluginResource implements QaServicePlugin {

	/**
	 * Default set of metrics collected from Sonar
	 */
	public static final String DEFAULT_METRICS = "ncloc,coverage,sqale_rating";

	/**
	 * Default set of metrics collected from Sonar 6.3+.
	 * <a href="http://localhost:9000/api/metrics/search">List of metrics</a>
	 */
	public static final String DEFAULT_METRICS_63 = DEFAULT_METRICS + ",security_rating,reliability_rating,security_review_rating";

	/**
	 * Plug-in key.
	 */
	public static final String URL = QaResource.SERVICE_URL + "/sonarqube";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	/**
	 * Default metrics defined at global level. Will be used to override the default version based metric set.
	 */
	public static final String DEFAULT_METRICS_OVERRIDE = KEY + ":default-metrics";

	@Autowired
	private ConfigurationResource configuration;

	/**
	 * Version utilities for compare.
	 */
	@Autowired
	protected VersionUtils versionUtils;

	@Value("${sonar.jira.url:https://sonarsource.atlassian.net}")
	protected String versionServer;

	/**
	 * Sonar username able to connect to instance.
	 */
	public static final String PARAMETER_USER = KEY + ":user";

	/**
	 * Sonar user password able to connect to instance.
	 */
	public static final String PARAMETER_PASSWORD = KEY + ":password";

	/**
	 * Sonar project's identifier, an integer
	 */
	public static final String PARAMETER_PROJECT = KEY + ":project";

	/**
	 * Web site URL
	 */
	public static final String PARAMETER_URL = KEY + ":url";

	@Override
	public void link(final int subscription) throws Exception {
		final var parameters = subscriptionResource.getParameters(subscription);

		// Validate the node settings
		validateAdminAccess(parameters);

		// Validate the project settings
		validateProject(parameters);
	}

	/**
	 * Validate the project connectivity.
	 *
	 * @param parameters the project parameters.
	 * @return project details.
	 * @throws IOException When JSON parsing failed.
	 */
	protected SonarProject validateProject(final Map<String, String> parameters) throws IOException {
		// Get project's configuration
		final var id = ObjectUtils.defaultIfNull(parameters.get(PARAMETER_PROJECT), "0");
		final var result = getProject(parameters, id);
		if (result == null) {
			// Invalid id
			throw new ValidationJsonException(PARAMETER_PROJECT, "sonar-project", id);
		}
		return result;
	}

	/**
	 * Validate the basic REST connectivity to SonarQube.
	 *
	 * @param parameters the server parameters.
	 * @return the detected SonarQube version.
	 */
	protected String validateAdminAccess(final Map<String, String> parameters) {
		final var url = StringUtils.appendIfMissing(parameters.get(PARAMETER_URL), "/") + "sessions/new";
		CurlProcessor.validateAndClose(url, PARAMETER_URL, "sonar-connection");

		// Check the user can logins to SonarQube with the preempted authentication processor
		if (!StringUtils.trimToEmpty(getResource(parameters, "api/authentication/validate?format=json"))
				.contains("true")) {
			throw new ValidationJsonException(PARAMETER_USER, "sonar-login");
		}

		// Check the user has enough rights to access to the provisioning page
		final var version = getVersion(parameters);
		if (StringUtils.isNotBlank(version)) {
			final String checkRights;
			if (is63API(version)) {
				checkRights = getResource(parameters, "api/projects/search");
			} else {
				checkRights = getResource(parameters, "provisioning");
			}
			if (checkRights == null) {
				throw new ValidationJsonException(PARAMETER_USER, "sonar-rights");
			}
		}
		return version;
	}

	private boolean is63API(final String version) {
		return version != null && version.compareTo("6.3.0") >= 0;
	}

	/**
	 * Return a SonarQube's resource. Return <code>null</code> when the resource is
	 * not found.
	 *
	 * @param parameters The subscription parameters.
	 * @param resource   The SonarQube resource URL to query.
	 * @return The JSON data.
	 */
	protected String getResource(final Map<String, String> parameters, final String resource) {
		return getResource(new SonarCurlProcessor(parameters), parameters.get(PARAMETER_URL), resource);
	}

	/**
	 * Return a SonarQube's resource. Return <code>null</code> when the resource is
	 * not found.
	 *
	 * @param processor The CURL processor.
	 * @param url       The base URL.
	 * @param resource  The SonarQube resource URL to query.
	 * @return The JSON data.
	 */
	protected String getResource(final CurlProcessor processor, final String url, final String resource) {
		// Get the resource using the preempted authentication
		return processor.get(StringUtils.appendIfMissing(url, "/") + resource);
	}

	@Override
	public String getVersion(final Map<String, String> parameters) {
		return getResource(parameters, "api/server/version");
	}

	/**
	 * Return all SonarQube project without limit.
	 *
	 * @param parameters     The subscription parameters.
	 * @param formatCriteria Optional criteria
	 * @return The gathered SonarQube projects data.
	 * @throws IOException When JSON parsing failed.
	 */
	protected List<SonarProject> getProjects(final Map<String, String> parameters, final String formatCriteria) throws IOException {
		final var version = getVersion(parameters);
		if (is63API(version)) {
			return new ObjectMapper().readValue(getResource(parameters, "api/projects/search?q=" + URLEncoder.encode(formatCriteria, StandardCharsets.UTF_8)),
					new TypeReference<SonarProjectList>() {
						// Nothing to override
					}).getComponents();
		}
		return new ObjectMapper().readValue(getResource(parameters, "api/resources?format=json"),
				new TypeReference<>() {
					// Nothing to override
				});
	}

	/**
	 * Return SonarQube project from its identifier.
	 *
	 * @param parameters The subscription parameters.
	 * @param id         The SonarQube project identifier (internal id or key).
	 * @return The gathered SonarQube data.
	 * @throws IOException When JSON parsing failed.
	 */
	protected SonarProject getProject(final Map<String, String> parameters, final String id) throws IOException {
		final var version = getVersion(parameters);
		final String encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
		final String projectAsJson;
		if (is63API(version)) {
			projectAsJson = getResource(parameters, "api/measures/component?component=" + encodedId
					+ "&metricKeys=" + configuration.get(DEFAULT_METRICS_OVERRIDE, DEFAULT_METRICS_63));
		} else {
			projectAsJson = getResource(parameters, "api/resources?format=json&resource=" + encodedId
					+ "&metrics=" + configuration.get(DEFAULT_METRICS_OVERRIDE, DEFAULT_METRICS));
		}
		if (projectAsJson == null) {
			return null;
		}

		// Parse and build the project from the JSON
		final SonarProject project;
		if (is63API(version)) {
			project = new ObjectMapper()
					.readValue(StringUtils.removeEnd(projectAsJson
							.replaceAll("\n", "")
							.replaceAll("\r", "")
							.replaceFirst("\\{[ \t]*\"component\"[ \t]*:[ \t]*\\{", "{"), "}"), SonarProject.class);
		} else {
			project = new ObjectMapper()
					.readValue(StringUtils.removeEnd(StringUtils.removeStart(projectAsJson, "["), "]"), SonarProject.class);
		}
		// Map nicely the measures
		project.setMeasuresAsMap(
				project.getRawMeasures().stream().collect(Collectors.toMap(SonarMeasure::getKey, v-> (int)v.getValue())));
		project.setRawMeasures(null);
		return project;
	}

	/**
	 * Search the SonarQube's projects matching to the given criteria. Name, display
	 * name and description are considered.
	 *
	 * @param node     the node to be tested with given parameters.
	 * @param criteria the search criteria.
	 * @return project names matching the criteria.
	 * @throws IOException When JSON parsing failed.
	 */
	@GET
	@Path("{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<SonarProject> findAllByName(@PathParam("node") final String node,
			@PathParam("criteria") final String criteria) throws IOException {

		// Prepare the context, an ordered set of projects
		final var format = new NormalizeFormat();
		final var formatCriteria = format.format(criteria);
		final var parameters = pvResource.getNodeParameters(node);

		// Get the projects and parse them
		final var projectsRaw = getProjects(parameters, formatCriteria);
		final var result = new TreeMap<String, SonarProject>();
		for (final var project : projectsRaw) {
			final var name = StringUtils.trimToNull(project.getName());
			final var key = project.getKey();
			if (project.getId() == null) {
				project.setId(project.getKey());
			}

			// Check the values of this project
			if (format.format(name).contains(formatCriteria) || format.format(key).contains(formatCriteria)) {

				// Retrieve description and display name
				result.put(project.getName(), project);
			}
		}
		return new ArrayList<>(result.values());
	}

	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public String getLastVersion() throws Exception {
		return versionUtils.getLatestReleasedVersionName(versionServer, "SONAR");
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) throws Exception {
		final var nodeStatusWithData = new SubscriptionStatusWithData();
		nodeStatusWithData.put("project", validateProject(parameters));
		return nodeStatusWithData;
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) {
		// Status is UP <=> Administration access is UP
		validateAdminAccess(parameters);
		return true;
	}

}
