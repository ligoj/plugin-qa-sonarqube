/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.sonarqube;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.curl.HttpResponseCallback;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Permission template operations on a SonarQube node: the template of a Ligoj project and the project permissions of
 * its groups. Each call uses its own processor, the authentication being sent with the first request of a processor
 * only. On SonarQube Cloud, every call is scoped to the node organization. A call SonarQube does not accept stops with
 * its cause: {@code sonar-connection} without answer, {@code sonar-admin} for refused credentials (401/403),
 * {@code sonar-error} with the SonarQube message otherwise.
 */
@Slf4j
class SonarQubeTemplates {

	/**
	 * Project permissions a template grants, as the ligoj CLI {@code SONAR_PERMISSIONS_PROJECT}.
	 */
	static final List<String> PERMISSIONS = List.of("user", "codeviewer", "issueadmin", "securityhotspotadmin", "scan", "admin");

	/**
	 * Projects per page of the project search, the SonarQube maximum.
	 */
	static final int PAGE_SIZE = 500;

	/**
	 * Pages read at most: 10,000 projects.
	 */
	static final int MAX_PAGES = 20;

	/**
	 * Regular expression characters ending the literal start of a pattern.
	 */
	private static final String REGEX_CHARS = "\\.[]{}()*+?^$|";

	/**
	 * Analysis dates of SonarQube, such as {@code 2026-10-10T08:00:00+0000}.
	 */
	private static final DateTimeFormatter ANALYSIS_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Paging(int total) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record ProjectPage(Paging paging, List<SonarQubeTemplateProject> components) {
	}

	/**
	 * A permission template.
	 *
	 * @param id                The SonarQube identifier.
	 * @param name              The unique name.
	 * @param projectKeyPattern The regular expression of the project keys it applies to, may be {@code null}.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record SonarQubeTemplate(String id, String name, String projectKeyPattern) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TemplateList(List<SonarQubeTemplate> permissionTemplates) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Group(String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GroupList(List<Group> groups) {
	}

	private final Map<String, String> parameters;

	private final ObjectMapper objectMapper;

	private final String baseUrl;

	/**
	 * The organization query parameter, empty for a SonarQube Server.
	 */
	private final String organization;

	SonarQubeTemplates(final Map<String, String> parameters, final ObjectMapper objectMapper) {
		this.parameters = parameters;
		this.objectMapper = objectMapper;
		this.baseUrl = Strings.CS.appendIfMissing(parameters.get(SonarQubePluginResource.PARAMETER_URL), "/");
		final var org = StringUtils.trimToNull(parameters.get(SonarQubePluginResource.PARAMETER_ORGANIZATION));
		this.organization = org == null ? "" : "&organization=" + encode(org);
	}

	static String encode(final String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/**
	 * Store every response body, whatever its status, the caller deciding from the status: the default callback
	 * accepts any status of a response without body and drops the body of the rejected ones.
	 */
	private static final HttpResponseCallback CAPTURE = (final CurlRequest request, final ClassicHttpResponse response) -> {
		final var entity = response.getEntity();
		if (entity != null) {
			try {
				request.setResponse(EntityUtils.toString(entity, StandardCharsets.UTF_8));
			} catch (final ParseException | IOException e) {
				log.warn("Unreadable SonarQube response of {} {}", request.getMethod(), CurlProcessor.toLogUrl(request.getUrl()));
			}
		}
		log.info("{} {} {}", response.getCode(), request.getMethod(), CurlProcessor.toLogUrl(request.getUrl()));
		return true;
	};

	/**
	 * Send a call, its parameters in the query as the SonarQube web API accepts them, and return the body of its
	 * accepted answer.
	 *
	 * @throws ValidationJsonException When SonarQube does not accept it: {@code sonar-connection} without answer,
	 *                                 {@code sonar-admin} for refused credentials, {@code sonar-error} otherwise.
	 */
	private String call(final String method, final String resource) {
		final var request = new CurlRequest(method, baseUrl + resource + organization, null, CAPTURE);
		request.setSaveResponse(true);
		try (var processor = new SonarQubeCurlProcessor(parameters)) {
			processor.process(request);
		}
		final var status = request.getStatus();
		if (status >= 200 && status < 300) {
			return request.getResponse();
		}
		if (status == 0) {
			throw new ValidationJsonException(SonarQubePluginResource.PARAMETER_URL, "sonar-connection");
		}
		if (status == 401 || status == 403) {
			throw new ValidationJsonException(SonarQubePluginResource.PARAMETER_USER, "sonar-admin");
		}
		throw new ValidationJsonException(SonarQubePluginResource.PARAMETER_URL, "sonar-error", "message", errorMessage(request.getResponse(), status));
	}

	/**
	 * Return the messages of a SonarQube error answer ({@code {"errors": [{"msg": "..."}]}}), the HTTP status without.
	 */
	private String errorMessage(final String body, final int status) {
		final var messages = new ArrayList<String>();
		try {
			objectMapper.readTree(StringUtils.defaultString(body)).path("errors").forEach(e -> messages.add(e.path("msg").asString()));
		} catch (final JacksonException e) {
			// Not a JSON answer
		}
		messages.removeIf(StringUtils::isBlank);
		return messages.isEmpty() ? "HTTP " + status : String.join(", ", messages);
	}

	/**
	 * Return the JSON of a resource.
	 */
	String get(final String resource) {
		return call("GET", resource);
	}

	/**
	 * Return the template of this name, {@code null} when SonarQube has none. The search matches a part of the name;
	 * the template names being unique whatever their case, the name is compared ignoring the case.
	 */
	SonarQubeTemplate find(final String name) {
		final var templates = objectMapper.readValue(get("api/permissions/search_templates?q=" + encode(name)), TemplateList.class)
				.permissionTemplates();
		return templates == null ? null : templates.stream().filter(t -> name.equalsIgnoreCase(t.name())).findFirst().orElse(null);
	}

	/**
	 * Create the template.
	 */
	void create(final String name, final String pattern, final String description) {
		call("POST", "api/permissions/create_template?name=" + encode(name) + "&projectKeyPattern=" + encode(pattern)
				+ "&description=" + encode(description));
	}

	/**
	 * Delete the template.
	 */
	void delete(final String name) {
		call("POST", "api/permissions/delete_template?templateName=" + encode(name));
	}

	/**
	 * Create the group when SonarQube does not know it yet, as the ligoj CLI.
	 */
	void ensureGroup(final String group) {
		final var groups = objectMapper.readValue(get("api/user_groups/search?q=" + encode(group)), GroupList.class).groups();
		if (groups == null || groups.stream().noneMatch(g -> group.equals(g.name()))) {
			call("POST", "api/user_groups/create?name=" + encode(group));
		}
	}

	/**
	 * Give the group exactly these project permissions on the template, as the ligoj CLI: the listed ones are added,
	 * the others removed. A removal SonarQube refuses, such as a permission the group does not have, is only logged.
	 */
	void grant(final String template, final String group, final Collection<String> permissions) {
		final var target = "?templateName=" + encode(template) + "&groupName=" + encode(group) + "&permission=";
		for (final var permission : PERMISSIONS) {
			if (permissions.contains(permission)) {
				call("POST", "api/permissions/add_group_to_template" + target + permission);
			} else {
				try {
					call("POST", "api/permissions/remove_group_from_template" + target + permission);
				} catch (final ValidationJsonException e) {
					log.info("Permission {} of group {} not removed from template {}: {}", permission, group, template, e.getErrors());
				}
			}
		}
	}

	/**
	 * Return the projects whose key matches the pattern, latest analysis first, the never analyzed ones last, then by
	 * key. The literal start of the pattern, when long enough, narrows the search on the server: the search matches a
	 * part of the key, so it returns a superset.
	 *
	 * @param pattern The project key pattern of the template, may be {@code null}.
	 * @return The matching projects, empty without a usable pattern.
	 */
	/**
	 * Whether a project key matches the project key pattern of a template, as for the projects listed by the status. A
	 * blank or unusable pattern matches nothing.
	 *
	 * @param pattern The project key pattern of the template.
	 * @param key     The project key.
	 * @return <code>true</code> when the key belongs to the template.
	 */
	static boolean matches(final String pattern, final String key) {
		if (StringUtils.isBlank(pattern)) {
			return false;
		}
		try {
			return Pattern.compile(pattern).matcher(key).matches();
		} catch (final PatternSyntaxException e) {
			log.info("Unusable project key pattern {}", pattern);
			return false;
		}
	}

	List<SonarQubeTemplateProject> projects(final String pattern) {
		if (StringUtils.isBlank(pattern)) {
			return List.of();
		}
		final Pattern regex;
		try {
			regex = Pattern.compile(pattern);
		} catch (final PatternSyntaxException e) {
			log.info("Unusable project key pattern {}", pattern);
			return List.of();
		}
		final var literal = literalPrefix(pattern);
		final var query = literal.length() < 2 ? "" : "&q=" + encode(literal);
		final var matching = new ArrayList<SonarQubeTemplateProject>();
		for (var page = 1; page <= MAX_PAGES; page++) {
			final var result = objectMapper.readValue(get("api/projects/search?ps=" + PAGE_SIZE + "&p=" + page + query), ProjectPage.class);
			Objects.requireNonNullElse(result.components(), List.<SonarQubeTemplateProject>of()).stream()
					.filter(p -> p.key() != null && regex.matcher(p.key()).matches()).forEach(matching::add);
			if (result.paging() == null || page * PAGE_SIZE >= result.paging().total()) {
				break;
			}
			if (page == MAX_PAGES) {
				log.info("Projects matching {} beyond the first {} are ignored", pattern, MAX_PAGES * PAGE_SIZE);
			}
		}
		matching.sort(Comparator.comparing(SonarQubeTemplates::analysisTime, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(SonarQubeTemplateProject::key));
		return matching;
	}

	/**
	 * Return the literal start every key matching the regular expression contains: up to its first special character,
	 * without the character a following {@code ?}, {@code *} or {@code {}} makes optional, and nothing with an
	 * alternation, whose other branches may start differently.
	 */
	static String literalPrefix(final String pattern) {
		if (pattern.indexOf('|') >= 0) {
			return "";
		}
		final var end = StringUtils.indexOfAny(pattern, REGEX_CHARS);
		if (end < 0) {
			return pattern;
		}
		return pattern.substring(0, "?*{".indexOf(pattern.charAt(end)) >= 0 ? Math.max(0, end - 1) : end);
	}

	/**
	 * Return the last analysis time, {@code null} when never analyzed or unreadable.
	 */
	static Instant analysisTime(final SonarQubeTemplateProject project) {
		try {
			return project.lastAnalysisDate() == null ? null : ANALYSIS_DATE.parse(project.lastAnalysisDate(), Instant::from);
		} catch (final DateTimeParseException e) {
			return null;
		}
	}
}
