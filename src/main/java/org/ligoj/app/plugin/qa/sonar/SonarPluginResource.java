package org.ligoj.app.plugin.qa.sonar;

import java.io.IOException;
import java.text.Format;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.plugin.qa.QaResource;
import org.ligoj.app.plugin.qa.QaServicePlugin;
import org.ligoj.app.resource.NormalizeFormat;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.VersionUtils;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sonar resource.
 */
@Path(SonarPluginResource.URL)
@Service
@Produces(MediaType.APPLICATION_JSON)
public class SonarPluginResource extends AbstractToolPluginResource implements QaServicePlugin {

	/**
	 * Plug-in key.
	 */
	public static final String URL = QaResource.SERVICE_URL + "/sonarqube";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	@Autowired
	protected VersionUtils versionUtils;

	@Value("${sonar.jira.url:https://jira.sonarsource.com}")
	private String versionServer;

	/**
	 * Sonar user name able to connect to instance.
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
		final Map<String, String> parameters = subscriptionResource.getParameters(subscription);

		// Validate the node settings
		validateAdminAccess(parameters);

		// Validate the project settings
		validateProject(parameters);
	}

	/**
	 * Validate the project connectivity.
	 * 
	 * @param parameters
	 *            the project parameters.
	 * @return project details.
	 */
	protected SonarProject validateProject(final Map<String, String> parameters) throws IOException {
		// Get project's configuration
		final int id = Integer.parseInt(ObjectUtils.defaultIfNull(parameters.get(PARAMETER_PROJECT), "0"));
		final SonarProject result = getProject(parameters, id);

		if (result == null) {
			// Invalid id
			throw new ValidationJsonException(PARAMETER_PROJECT, "sonar-project", id);
		}

		return result;
	}

	/**
	 * Validate the basic REST connectivity to SonarQube.
	 * 
	 * @param parameters
	 *            the server parameters.
	 * @return the detected SonarQube version.
	 */
	protected String validateAdminAccess(final Map<String, String> parameters) throws Exception {
		final String url = StringUtils.appendIfMissing(parameters.get(PARAMETER_URL), "/") + "sessions/new";
		CurlProcessor.validateAndClose(url, PARAMETER_URL, "sonar-connection");

		// Check the user can log-in to SonarQube with the preempted authentication processor
		if (!StringUtils.trimToEmpty(getResource(parameters, "api/authentication/validate?format=json")).contains("true")) {
			throw new ValidationJsonException(PARAMETER_USER, "sonar-login");
		}

		// Check the user has enough rights to access to the provisioning page
		if (getResource(parameters, "provisioning") == null) {
			throw new ValidationJsonException(PARAMETER_USER, "sonar-rights");
		}
		return getVersion(parameters);
	}

	/**
	 * Return a SonarQube's resource. Return <code>null</code> when the resource is not found.
	 */
	protected String getResource(final Map<String, String> parameters, final String resource) {
		return getResource(new SonarCurlProcessor(parameters), parameters.get(PARAMETER_URL), resource);
	}

	/**
	 * Return a SonarQube's resource. Return <code>null</code> when the resource is not found.
	 */
	protected String getResource(final CurlProcessor processor, final String url, final String resource) {
		// Get the resource using the preempted authentication
		return processor.get(StringUtils.appendIfMissing(url, "/") + resource);
	}

	@Override
	public String getVersion(final Map<String, String> parameters) throws Exception {
		final String sonarVersionAsJson = ObjectUtils.defaultIfNull(getResource(parameters, "api/server/index?format=json"), "{}");
		return (String) new ObjectMapper().readValue(sonarVersionAsJson, Map.class).get("version");
	}

	/**
	 * Return all SonarQube project without limit.
	 */
	protected List<SonarProject> getProjects(final Map<String, String> parameters) throws IOException {
		return new ObjectMapper().readValue(getResource(parameters, "api/resources?format=json"), new TypeReference<List<SonarProject>>() {
			// Nothing to override
		});
	}

	/**
	 * Return SonarQube project from its identifier.
	 */
	protected SonarProject getProject(final Map<String, String> parameters, final int id) throws IOException {
		final String projectAsJson = getResource(parameters, "api/resources?format=json&resource=" + id + "&metrics=ncloc,coverage,sqale_rating");
		if (projectAsJson == null) {
			return null;
		}

		// Parse and build the project from the JSON
		final SonarProject project = new ObjectMapper().readValue(StringUtils.removeEnd(StringUtils.removeStart(projectAsJson, "["), "]"),
				SonarProject.class);

		// Map nicely the measures
		project.setMeasures(project.getRawMesures().stream().collect(Collectors.toMap(SonarMesure::getKey, SonarMesure::getValue)));
		project.setRawMesures(null);
		return project;
	}

	/**
	 * Search the SonarQube's projects matching to the given criteria. Name, display name and description are
	 * considered.
	 * 
	 * @param node
	 *            the node to be tested with given parameters.
	 * @param criteria
	 *            the search criteria.
	 * @return project names matching the criteria.
	 */
	@GET
	@Path("{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<SonarProject> findAllByName(@PathParam("node") final String node, @PathParam("criteria") final String criteria) throws IOException {

		// Prepare the context, an ordered set of projects
		final Format format = new NormalizeFormat();
		final String formatCriteria = format.format(criteria);
		final Map<String, String> parameters = pvResource.getNodeParameters(node);

		// Get the projects and parse them
		final List<SonarProject> projectsRaw = getProjects(parameters);
		final Map<String, SonarProject> result = new TreeMap<>();
		for (final SonarProject project : projectsRaw) {
			final String name = StringUtils.trimToNull(project.getName());
			final String key = project.getKey();

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
		final SubscriptionStatusWithData nodeStatusWithData = new SubscriptionStatusWithData();
		nodeStatusWithData.put("project", validateProject(parameters));
		return nodeStatusWithData;
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) throws Exception {
		// Status is UP <=> Administration access is UP
		validateAdminAccess(parameters);
		return true;
	}

}
