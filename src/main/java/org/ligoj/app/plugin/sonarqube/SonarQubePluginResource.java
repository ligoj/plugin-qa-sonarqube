/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.sonarqube;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.ligoj.app.api.SubscriptionMode;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.dao.ParameterRepository;
import org.ligoj.app.plugin.qa.QaResource;
import org.ligoj.app.plugin.qa.QaServicePlugin;
import org.ligoj.app.resource.NormalizeFormat;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.VersionUtils;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Sonar resource.
 */
@Path(SonarQubePluginResource.URL)
@Service
@Produces(MediaType.APPLICATION_JSON)
@Slf4j
public class SonarQubePluginResource extends AbstractToolPluginResource implements QaServicePlugin {

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
	public static final String PARAMETER_METRICS_OVERRIDE = KEY + ":metrics";

	/**
	 * Maximum returned branches.
	 */
	public static final String PARAMETER_MAX_BRANCHES = KEY + ":max-branches";

	/**
	 * Metrics retrieved from each branch
	 */
	public static final String PARAMETER_METRICS_BRANCHES = KEY + ":metrics-branches";

	/**
	 * Default maximum returned branches.
	 */
	public static final int DEFAULT_MAX_BRANCHES = 10;

	/**
	 * Sonar username able to connect to instance.
	 */
	public static final String PARAMETER_USER = KEY + ":user";

	/**
	 * Sonar user password/token able to connect to instance.
	 */
	public static final String PARAMETER_PASSWORD = KEY + ":password";

	/**
	 * Sonar project's identifier, an integer
	 */
	public static final String PARAMETER_PROJECT = KEY + ":project";

	/**
	 * Website URL
	 */
	public static final String PARAMETER_URL = KEY + ":url";

	/**
	 * SonarQube Cloud organization key, required to search its projects. A SonarQube Server has no organization.
	 */
	public static final String PARAMETER_ORGANIZATION = KEY + ":organization";

	/**
	 * Name of the permission template created in CREATE mode, the project key by default.
	 */
	public static final String PARAMETER_TEMPLATE = KEY + ":template";

	/**
	 * Regular expression of the project keys the permission template applies to.
	 */
	public static final String PARAMETER_TEMPLATE_PATTERN = KEY + ":template-pattern";

	/**
	 * Role mapping of the permission template: a JSON object keyed by group, each value the list of its project
	 * permissions.
	 */
	public static final String PARAMETER_ROLES = KEY + ":roles";

	@Autowired
	private ObjectMapperTrim objectMapper;

	/**
	 * Version utilities for compare.
	 */
	@Autowired
	protected VersionUtils versionUtils;

	@Value("${sonar.jira.url:https://sonarsource.atlassian.net}")
	protected String versionServer;

	@Autowired
	private NodeRepository nodeRepository;

	@Autowired
	private ParameterRepository parameterRepository;

	@Override
	public void link(final int subscription) {
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
	 */
	protected SonarQubeProject validateProject(final Map<String, String> parameters)  {
		// Get project's configuration
		final var id = ObjectUtils.getIfNull(parameters.get(PARAMETER_PROJECT), "0");
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
		final var url = Strings.CS.appendIfMissing(parameters.get(PARAMETER_URL), "/") + "sessions/new";

		try (final var curlProcessor = new CurlProcessor(CurlProcessor.DEFAULT_CALLBACK, CurlProcessor.DEFAULT_CONNECTION_TIMEOUT,
				CurlProcessor.DEFAULT_RESPONSE_TIMEOUT, true, null, null)) {
			curlProcessor.validate(url, PARAMETER_URL, "sonar-connection");
		}

		// Check the user can logins to SonarQube with the preempted authentication processor
		final var version = getVersion(parameters);
		if (!StringUtils.trimToEmpty(getResource(parameters, "api/authentication/validate?format=json"))
				.contains("true")) {
			throw new ValidationJsonException(PARAMETER_USER, "sonar-login");
		}

		// Check the user has enough rights to link a project: browsing the projects. The administration rights are not
		// required, a global analysis token is enough.
		if (StringUtils.isNotBlank(version)) {
			final String checkRights;
			if (is63API(version)) {
				checkRights = getResource(parameters, "api/components/search?qualifiers=TRK&ps=1" + organizationFilter(parameters));
			} else {
				checkRights = getResource(parameters, "provisioning");
			}
			if (checkRights == null) {
				validateOrganization(parameters);
				throw new ValidationJsonException(PARAMETER_USER, "sonar-rights");
			}
		}
		return version;
	}

	/**
	 * Explain a refused project search by the organization of SonarQube Cloud, the only server having organizations: it
	 * searches the projects of an existing organization only.
	 *
	 * @param parameters the server parameters.
	 */
	private void validateOrganization(final Map<String, String> parameters) {
		final var organization = StringUtils.trimToNull(parameters.get(PARAMETER_ORGANIZATION));
		final var organizations = getResource(parameters, "api/organizations/search?"
				+ (organization == null ? "member=true" : "organizations=" + URLEncoder.encode(organization, StandardCharsets.UTF_8)));
		if (organizations == null) {
			// Not SonarQube Cloud: the organization is not the cause
			return;
		}
		if (organization == null) {
			throw new ValidationJsonException(PARAMETER_ORGANIZATION, "NotBlank");
		}
		if (objectMapper.readTree(organizations).path("organizations").isEmpty()) {
			throw new ValidationJsonException(PARAMETER_ORGANIZATION, "sonar-organization", organization);
		}
	}

	/**
	 * Return the query parameter restricting a project search to the organization of the node, required by SonarQube
	 * Cloud.
	 *
	 * @param parameters the server parameters.
	 * @return The query parameter, empty without organization.
	 */
	private String organizationFilter(final Map<String, String> parameters) {
		final var organization = StringUtils.trimToNull(parameters.get(PARAMETER_ORGANIZATION));
		return organization == null ? "" : "&organization=" + URLEncoder.encode(organization, StandardCharsets.UTF_8);
	}

	private boolean is63API(final String version) {
		return version != null && new DefaultArtifactVersion(version).compareTo(new DefaultArtifactVersion("6.3.0")) >= 0;
	}

	private boolean is66API(final String version) {
		return version != null && new DefaultArtifactVersion(version).compareTo(new DefaultArtifactVersion("6.6.0")) >= 0;
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
		return getResource(new SonarQubeCurlProcessor(parameters), parameters.get(PARAMETER_URL), resource);
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
		return processor.get(Strings.CS.appendIfMissing(url, "/") + resource);
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
	 */
	protected List<SonarQubeProject> getProjects(final Map<String, String> parameters, final String formatCriteria) {
		final var version = getVersion(parameters);
		if (is63API(version)) {
			final var query = URLEncoder.encode(formatCriteria, StandardCharsets.UTF_8);
			final var organization = organizationFilter(parameters);
			// The administration search also matches a part of the project key, but requires the "Administer System"
			// permission
			var projectsAsJson = getResource(parameters, "api/projects/search?q=" + query + organization);
			if (projectsAsJson == null) {
				// Without this permission, such as with a global analysis token: the projects the credentials can browse.
				// A query of less than 2 characters is refused, the projects are then filtered by the caller.
				projectsAsJson = getResource(parameters, "api/components/search?qualifiers=TRK" + (formatCriteria.length() < 2 ? "" : "&q=" + query) + organization);
			}
			return objectMapper.readValue(projectsAsJson, new TypeReference<SonarQubeProjectList>() {
				// Nothing to override
			}).getComponents();
		}
		return objectMapper.readValue(getResource(parameters, "api/resources?format=json"),
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
	 */
	protected SonarQubeProject getProject(final Map<String, String> parameters, final String id) {
		final var version = getVersion(parameters);
		final var encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8);
		List<SonarQubeBranch> branches = Collections.emptyList();

		// Get the JSON project
		final String queryUrl;
		final String defaultMetrics;
		if (is63API(version)) {
			queryUrl = "api/measures/component?component=" + encodedId + "&metricKeys=";
			defaultMetrics = DEFAULT_METRICS_63;
		} else {
			queryUrl = "api/resources?format=json&resource=" + encodedId + "&metrics=";
			defaultMetrics = DEFAULT_METRICS;
		}
		var projectAsJson = getResource(parameters, queryUrl + getParameter(parameters, PARAMETER_METRICS_OVERRIDE, defaultMetrics));
		if (projectAsJson == null) {
			return null;
		}

		// Parse the JSON project from the JSON
		final SonarQubeProject project;
		if (is63API(version)) {
			project = objectMapper.readValue(unwrap(projectAsJson), SonarQubeProject.class);
			if (is66API(version)) {
				// Parse and build the project's branches from the JSON
				final int maxBranches = NumberUtils.toInt(getParameter(parameters, PARAMETER_MAX_BRANCHES, String.valueOf(DEFAULT_MAX_BRANCHES)));
				if (maxBranches > 1) {
					branches = getSonarBranches(parameters, encodedId, maxBranches, defaultMetrics, queryUrl);
				}
			}
		} else {
			project = objectMapper.readValue(Strings.CS.removeEnd(Strings.CS.removeStart(projectAsJson, "["), "]"), SonarQubeProject.class);
		}

		// Map nicely the measures
		project.setMeasuresAsMap(sanitizeMeasures(project));
		project.setBranches(branches);
		project.setRawMeasures(null);
		return project;
	}

	/**
	 * Retrieve branch details of a project. Only for 6.6 SonarQube versions.
	 */
	private List<SonarQubeBranch> getSonarBranches(final Map<String, String> parameters, final String encodedId, final int maxBranches,
			final String defaultMetrics, final String queryUrl) {
		final var branchesAsJson = getResource(parameters, "api/project_branches/list?project=" + encodedId);
		final var branches = objectMapper.readValue(unwrap(Objects.requireNonNullElse(branchesAsJson, "{}")),
						new TypeReference<List<SonarQubeBranch>>() {
							// Nothing to override
						}).stream()
				.sorted((b1, b2) -> {
					// Sort the branches by their activities
					if (b1.isMain()) {
						return -1;
					}
					if (b2.isMain()) {
						return 1;
					}
					return Strings.CS.compare(b2.getAnalysisDate(), b1.getAnalysisDate());
				}).limit(maxBranches).toList();
		final var branchMetrics = getParameter(parameters, PARAMETER_METRICS_BRANCHES, defaultMetrics);
		if (!branchMetrics.isBlank()) {
			// Get more metrics from each branch
			branches.parallelStream().forEach(b -> {
				final var branchesMetricsAsJson = getResource(parameters, queryUrl + branchMetrics
						+ "&branch=" + URLEncoder.encode(b.getName(), StandardCharsets.UTF_8));
				if (branchesMetricsAsJson != null) {
					try {
						final var branchesMetrics = objectMapper.readValue(unwrap(branchesMetricsAsJson), SonarQubeProject.class);

						// Complete with the branch measures
						b.setMeasuresAsMap(sanitizeMeasures(branchesMetrics));
					} catch (JacksonException je) {
						log.warn("Unable to parse branch metrics {}", b.getName(), je);
					}
				}
			});
		}
		return branches;
	}

	private Map<String, Integer> sanitizeMeasures(SonarQubeProject project) {
		return project.getRawMeasures().stream().collect(Collectors.toMap(SonarQubeMeasure::getKey, v -> (int) v.getValue()));
	}

	/**
	 * Remove the wrapping '{..}' and return the first property
	 */
	private String unwrap(final String original) {
		return original == null ? null : Strings.CS.removeEnd(original
				.replace("\n", "").replace("\r", "")
				.replaceFirst("\\{[ \t]*\"[a-zA-Z0-9_-]+\"[ \t]*:[ \t]*", ""), "}");
	}

	/**
	 * Search the SonarQube's projects matching to the given criteria. Name, display
	 * name and description are considered.
	 *
	 * @param node     the node to be tested with given parameters.
	 * @param criteria the search criteria.
	 * @return project names matching the criteria.
	 */
	@GET
	@Path("{node}/{criteria}")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<SonarQubeProject> findAllByName(@PathParam("node") final String node,
			@PathParam("criteria") final String criteria) {

		// Prepare the context, an ordered set of projects
		final var format = new NormalizeFormat();
		final var formatCriteria = format.format(criteria);
		final var parameters = pvResource.getNodeParameters(node);

		// Get the projects and parse them
		final var projectsRaw = getProjects(parameters, formatCriteria);
		final var result = new TreeMap<String, SonarQubeProject>();
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

	/**
	 * Sink of the non-blocking warnings of a subscription creation or deletion, reported to the caller.
	 */
	protected ResponseWarnings.Sink responseWarnings() {
		return ResponseWarnings::add;
	}

	private SonarQubeTemplates newTemplates(final Map<String, String> parameters) {
		return new SonarQubeTemplates(parameters, objectMapper);
	}

	/**
	 * Create the permission template of the project, or reuse a template of the same name and pattern, then give each
	 * group of the role mapping its project permissions on it. Everything is validated before the first remote call.
	 * The existing projects keep their permissions: SonarQube applies a template when a project is created.
	 */
	@Override
	public void create(final int subscription) {
		final var parameters = subscriptionResource.getParameters(subscription);
		final var name = StringUtils.trimToEmpty(parameters.get(PARAMETER_TEMPLATE));
		if (name.isEmpty()) {
			throw new ValidationJsonException(PARAMETER_TEMPLATE, "NotBlank");
		}
		final var pattern = StringUtils.trimToEmpty(parameters.get(PARAMETER_TEMPLATE_PATTERN));
		if (pattern.isEmpty()) {
			throw new ValidationJsonException(PARAMETER_TEMPLATE_PATTERN, "NotBlank");
		}
		try {
			Pattern.compile(pattern);
		} catch (final PatternSyntaxException e) {
			throw new ValidationJsonException(PARAMETER_TEMPLATE_PATTERN, "sonar-template-pattern");
		}
		final var roles = parseRoles(parameters.get(PARAMETER_ROLES));
		validateAdminAccess(parameters);

		final var templates = newTemplates(parameters);
		final var existing = templates.find(name);
		final String template;
		if (existing == null) {
			final var description = "Ligoj project " + subscriptionRepository.findOneExpected(subscription).getProject().getName();
			templates.create(name, pattern, description);
			template = name;
		} else if (pattern.equals(existing.projectKeyPattern())) {
			// Reused under its own name, the names being unique whatever their case
			template = existing.name();
			log.info("SonarQube permission template {} already exists, reused", template);
			responseWarnings().warn("sonar-template-reused", ResponseWarnings.parameters("template", template));
		} else {
			throw new ValidationJsonException(PARAMETER_TEMPLATE_PATTERN, "sonar-template-exists", "pattern",
					Objects.toString(existing.projectKeyPattern(), ""));
		}
		roles.forEach((group, permissions) -> {
			templates.ensureGroup(group);
			templates.grant(template, group, permissions);
		});
	}

	/**
	 * Delete the remote data of a template-based subscription: its permission template. The groups are kept, often
	 * shared or synchronized from LDAP, and the projects too, created by the scanners. A template already gone is a
	 * warning.
	 */
	@Override
	public void delete(final int subscription, final boolean deleteRemoteData) {
		if (!deleteRemoteData) {
			return;
		}
		final var parameters = subscriptionResource.getParameters(subscription);
		final var name = StringUtils.trimToNull(parameters.get(PARAMETER_TEMPLATE));
		if (name == null) {
			// A linked project is not a remote data of Ligoj
			return;
		}
		final var templates = newTemplates(parameters);
		final var template = templates.find(name);
		if (template == null) {
			log.info("SonarQube permission template {} already deleted", name);
			responseWarnings().warn("sonar-template-missing", ResponseWarnings.parameters("template", name));
		} else {
			templates.delete(template.name());
		}
	}

	/**
	 * Parse and validate the role mapping, trimmed first: a JSON object keyed by group, each value a non-empty list of
	 * project permissions. Blank: no group permission.
	 */
	private Map<String, List<String>> parseRoles(final String raw) {
		final var json = StringUtils.trimToNull(raw);
		if (json == null) {
			return Map.of();
		}
		final Map<String, List<String>> roles;
		try {
			roles = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, List<String>>>() {
				// Nothing to extend
			});
		} catch (final JacksonException e) {
			log.info("Invalid SonarQube role mapping: {}", e.getOriginalMessage());
			throw new ValidationJsonException(PARAMETER_ROLES, "sonar-roles-json");
		}
		roles.forEach((group, permissions) -> {
			if (StringUtils.isBlank(group) || permissions == null || permissions.isEmpty()) {
				throw new ValidationJsonException(PARAMETER_ROLES, "sonar-roles-json");
			}
			// A null permission is reported as "null": findFirst() refuses a null element
			permissions.stream().filter(p -> p == null || !SonarQubeTemplates.PERMISSIONS.contains(p)).map(String::valueOf)
					.findFirst().ifPresent(p -> {
						throw new ValidationJsonException(PARAMETER_ROLES, "sonar-roles-permission", "group", group, "permission", p);
					});
		});
		return roles;
	}

	/**
	 * Upgrade the installed data, the CSV seed being insert-only: the tool node accepts the creation of a permission
	 * template, and the project is asked in LINK mode only, the CREATE mode finding the projects by template.
	 */
	@Override
	public void update(final String oldVersion) {
		nodeRepository.findById(KEY).filter(n -> n.getMode() == SubscriptionMode.LINK).ifPresent(n -> {
			log.info("Node {} now supports the permission template creation", KEY);
			n.setMode(SubscriptionMode.ALL);
		});
		parameterRepository.findById(PARAMETER_PROJECT).filter(p -> p.getMode() != SubscriptionMode.LINK).ifPresent(p -> {
			log.info("Parameter {} is now asked in LINK mode only", PARAMETER_PROJECT);
			p.setMode(SubscriptionMode.LINK);
		});
	}

	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public String getLastVersion() {
		return versionUtils.getLatestReleasedVersionName(versionServer, "SONAR");
	}

	/**
	 * Projects of a template listed in the status data at most.
	 */
	static final int LISTED_PROJECTS = 20;

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) {
		final var nodeStatusWithData = new SubscriptionStatusWithData();
		final var template = StringUtils.trimToNull(parameters.get(PARAMETER_TEMPLATE));
		if (template == null) {
			nodeStatusWithData.put("project", validateProject(parameters));
		} else {
			checkTemplateStatus(parameters, template, nodeStatusWithData);
		}
		return nodeStatusWithData;
	}

	/**
	 * Gather the projects of the permission template: the template as SonarQube has it now, the matching projects, and
	 * the details of the latest analyzed one in the shape of a linked project.
	 */
	private void checkTemplateStatus(final Map<String, String> parameters, final String name, final SubscriptionStatusWithData data) {
		final var templates = newTemplates(parameters);
		final var template = templates.find(name);
		if (template == null) {
			throw new ValidationJsonException(PARAMETER_TEMPLATE, "sonar-template", "name", name);
		}
		final var projects = templates.projects(template.projectKeyPattern());
		data.put("template", Map.of("name", template.name(), "pattern", StringUtils.defaultString(template.projectKeyPattern())));
		data.put("projects", List.copyOf(projects.subList(0, Math.min(LISTED_PROJECTS, projects.size()))));
		data.put("projectsCount", projects.size());
		projects.stream().filter(p -> SonarQubeTemplates.analysisTime(p) != null).findFirst().map(p -> getProject(parameters, p.key()))
				.ifPresent(p -> data.put("project", p));
	}

	/**
	 * Return the details of a project of a permission template subscription, as for its latest analyzed project: the
	 * metrics and the branches. Only a project matching the template pattern, as SonarQube has it now, is read: the
	 * node credentials can read any project.
	 *
	 * @param subscription The permission template subscription, visible by the principal user.
	 * @param key          The project key.
	 * @return The project details.
	 */
	@GET
	@Path("{subscription:\\d+}/project/{key}")
	public SonarQubeProject getTemplateProject(@PathParam("subscription") final int subscription, @PathParam("key") final String key) {
		final var parameters = subscriptionResource.getParameters(subscription);
		final var name = StringUtils.trimToNull(parameters.get(PARAMETER_TEMPLATE));
		if (name == null) {
			// A linked project subscription
			throw new ValidationJsonException(PARAMETER_PROJECT, "sonar-project", key);
		}
		final var template = newTemplates(parameters).find(name);
		if (template == null) {
			throw new ValidationJsonException(PARAMETER_TEMPLATE, "sonar-template", "name", name);
		}
		if (!SonarQubeTemplates.matches(template.projectKeyPattern(), key)) {
			throw new ValidationJsonException(PARAMETER_PROJECT, "sonar-project", key);
		}
		return Optional.ofNullable(getProject(parameters, key))
				.orElseThrow(() -> new ValidationJsonException(PARAMETER_PROJECT, "sonar-project", key));
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) {
		// Status is UP <=> Administration access is UP
		validateAdminAccess(parameters);
		return true;
	}

}
