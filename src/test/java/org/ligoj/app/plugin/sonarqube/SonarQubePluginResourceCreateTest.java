/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.sonarqube;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import jakarta.transaction.Transactional;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.api.SubscriptionMode;
import org.ligoj.app.model.*;
import org.ligoj.app.plugin.qa.QaResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Test class of {@link SonarQubePluginResource} in CREATE mode: the permission template of the project, its group
 * permissions, the projects it applies to, and the remote data deletion.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class SonarQubePluginResourceCreateTest extends AbstractServerTest {

	/**
	 * The role mapping of the Jupiter project.
	 */
	private static final String ROLES = """
			{"ligoj-jupiter-admin": ["admin", "user"], "ligoj-jupiter-dev": ["user", "codeviewer", "scan"]}""";

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private SubscriptionResource subscriptionResource;

	private SonarQubePluginResource resource;

	private final List<String> warnings = new ArrayList<>();

	private int subscription;

	@BeforeEach
	void prepareData() throws IOException {
		persistEntities("csv", new Class<?>[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class },
				StandardCharsets.UTF_8);
		this.subscription = getSubscription("Jupiter", QaResource.SERVICE_KEY);
		resource = new SonarQubePluginResource() {
			@Override
			protected ResponseWarnings.Sink responseWarnings() {
				return (code, parameters) -> warnings.add(code + " " + new TreeMap<>(parameters));
			}
		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// The Jupiter subscription in CREATE mode: a template instead of a project
		setParameter(SonarQubePluginResource.PARAMETER_PROJECT, null);
		setParameter(SonarQubePluginResource.PARAMETER_TEMPLATE, "ligoj-jupiter");
		setParameter(SonarQubePluginResource.PARAMETER_TEMPLATE_PATTERN, "ligoj-jupiter:.*");
		setParameter(SonarQubePluginResource.PARAMETER_ROLES, ROLES);
	}

	/**
	 * Replace a subscription parameter value.
	 */
	private void setParameter(final String parameter, final String value) {
		em.createQuery("DELETE ParameterValue WHERE parameter.id = :p AND subscription.id = :s").setParameter("p", parameter)
				.setParameter("s", subscription).executeUpdate();
		if (value != null) {
			final var pv = new ParameterValue();
			pv.setParameter(em.find(Parameter.class, parameter));
			pv.setSubscription(em.find(Subscription.class, subscription));
			pv.setData(value);
			em.persist(pv);
		}
		em.flush();
		cacheManager.getCache("subscription-parameters").clear();
	}

	private static final String SEARCH_TEMPLATE = "/api/permissions/search_templates?q=ligoj-jupiter";

	private static final String CREATE_TEMPLATE = "/api/permissions/create_template?name=ligoj-jupiter&projectKeyPattern=ligoj-jupiter%3A.*&description=Ligoj+project+Jupiter";

	/**
	 * The node user administers SonarQube 26.6.
	 */
	private void mockAdmin() {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/server/version")).willReturn(ok("26.6.0.123539")));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json")).willReturn(okJson("{\"valid\":true}")));
		httpServer.stubFor(get(urlEqualTo("/api/components/search?qualifiers=TRK&ps=1")).willReturn(okJson("{\"components\":[]}")));
	}

	/**
	 * The templates found by the search of the Jupiter template.
	 */
	private void mockTemplates(final String templates) {
		httpServer.stubFor(get(urlEqualTo(SEARCH_TEMPLATE)).willReturn(okJson("{\"permissionTemplates\":[" + templates + "]}")));
	}

	/**
	 * SonarQube knows the group, or not.
	 */
	private void mockGroup(final String encoded, final String group, final boolean exists) {
		httpServer.stubFor(get(urlEqualTo("/api/user_groups/search?q=" + encoded))
				.willReturn(okJson(exists ? "{\"groups\":[{\"name\":\"" + group + "\"}]}" : "{\"groups\":[{\"name\":\"" + group + "-old\"}]}")));
	}

	/**
	 * A refusal as SonarQube sends it: with a JSON body. The default callback accepts any response without body.
	 */
	private static ResponseDefinitionBuilder forbidden() {
		return aResponse().withStatus(HttpStatus.SC_FORBIDDEN).withBody("{\"errors\":[{\"msg\":\"Insufficient privileges\"}]}");
	}

	/**
	 * Every write succeeds.
	 */
	private void mockWrites() {
		httpServer.stubFor(post(urlPathMatching("/api/.*")).willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));
	}

	/**
	 * The group got exactly these permissions on the Jupiter template, the others removed.
	 */
	private void assertPermissions(final String encodedGroup, final String... granted) {
		for (final var permission : SonarQubeTemplates.PERMISSIONS) {
			final var action = Arrays.asList(granted).contains(permission) ? "add_group_to_template" : "remove_group_from_template";
			httpServer.verify(postRequestedFor(urlEqualTo("/api/permissions/" + action + "?templateName=ligoj-jupiter&groupName=" + encodedGroup
					+ "&permission=" + permission)));
		}
	}

	@Test
	void createNewTemplate() throws Exception {
		mockAdmin();
		// A template of a close name is not this one
		mockTemplates("{\"id\":\"T0\",\"name\":\"ligoj-jupiter-old\",\"projectKeyPattern\":\"old:.*\"}");
		mockGroup("ligoj-jupiter-admin", "ligoj-jupiter-admin", true);
		mockGroup("ligoj-jupiter-dev", "ligoj-jupiter-dev", false);
		mockWrites();
		httpServer.start();

		resource.create(subscription);
		httpServer.verify(postRequestedFor(urlEqualTo(CREATE_TEMPLATE)));
		httpServer.verify(0, postRequestedFor(urlEqualTo("/api/user_groups/create?name=ligoj-jupiter-admin")));
		httpServer.verify(postRequestedFor(urlEqualTo("/api/user_groups/create?name=ligoj-jupiter-dev")));
		assertPermissions("ligoj-jupiter-admin", "admin", "user");
		assertPermissions("ligoj-jupiter-dev", "user", "codeviewer", "scan");
		Assertions.assertEquals(List.of(), warnings);
	}

	@Test
	void createReusedTemplate() throws Exception {
		mockAdmin();
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"ligoj-jupiter:.*\"}");
		mockGroup("ligoj-jupiter-admin", "ligoj-jupiter-admin", true);
		mockGroup("ligoj-jupiter-dev", "ligoj-jupiter-dev", true);
		mockWrites();
		httpServer.start();

		resource.create(subscription);
		httpServer.verify(0, postRequestedFor(urlPathEqualTo("/api/permissions/create_template")));
		assertPermissions("ligoj-jupiter-dev", "user", "codeviewer", "scan");
		Assertions.assertEquals(List.of("sonar-template-reused {template=ligoj-jupiter}"), warnings);
	}

	@Test
	void createOtherPattern() {
		mockAdmin();
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"jupiter\\\\..*\"}");
		mockWrites();
		httpServer.start();

		final var e = Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(subscription));
		MatcherUtil.assertThrows(e, SonarQubePluginResource.PARAMETER_TEMPLATE_PATTERN, "sonar-template-exists");
		httpServer.verify(0, postRequestedFor(urlPathMatching("/api/.*")));
	}

	@Test
	void createInvalidPattern() {
		setParameter(SonarQubePluginResource.PARAMETER_TEMPLATE_PATTERN, "ligoj-jupiter:(");
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(subscription)),
				SonarQubePluginResource.PARAMETER_TEMPLATE_PATTERN, "sonar-template-pattern");
		httpServer.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void createBlankTemplate() {
		setParameter(SonarQubePluginResource.PARAMETER_TEMPLATE, "  ");
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(subscription)),
				SonarQubePluginResource.PARAMETER_TEMPLATE, "NotBlank");
	}

	@Test
	void createInvalidRoles() {
		httpServer.start();
		for (final var roles : List.of("[\"admin\"]", "{\"dev\": \"user\"}", "{\"dev\": []}", "{\" \": [\"user\"]}", "{oops")) {
			setParameter(SonarQubePluginResource.PARAMETER_ROLES, roles);
			MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(subscription)),
					SonarQubePluginResource.PARAMETER_ROLES, "sonar-roles-json");
		}
		for (final var roles : List.of("{\"dev\": [\"write\"]}", "{\"dev\": [null]}")) {
			setParameter(SonarQubePluginResource.PARAMETER_ROLES, roles);
			MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(subscription)),
					SonarQubePluginResource.PARAMETER_ROLES, "sonar-roles-permission");
		}
		httpServer.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void createNoRoles() throws Exception {
		setParameter(SonarQubePluginResource.PARAMETER_ROLES, " ");
		mockAdmin();
		mockTemplates("");
		mockWrites();
		httpServer.start();

		resource.create(subscription);
		httpServer.verify(postRequestedFor(urlEqualTo(CREATE_TEMPLATE)));
		httpServer.verify(0, getRequestedFor(urlPathEqualTo("/api/user_groups/search")));
		httpServer.verify(0, postRequestedFor(urlPathMatching("/api/permissions/.*_group_.*")));
	}

	@Test
	void createEncodesNames() throws Exception {
		setParameter(SonarQubePluginResource.PARAMETER_ROLES, "{\"team a+b&c\": [\"user\"]}");
		mockAdmin();
		mockTemplates("");
		mockGroup("team+a%2Bb%26c", "team a+b&c", false);
		mockWrites();
		httpServer.start();

		resource.create(subscription);
		httpServer.verify(postRequestedFor(urlEqualTo("/api/user_groups/create?name=team+a%2Bb%26c")));
		assertPermissions("team+a%2Bb%26c", "user");
	}

	@Test
	void createRefused() {
		mockAdmin();
		mockTemplates("");
		httpServer.stubFor(post(urlPathEqualTo("/api/permissions/create_template")).willReturn(forbidden()));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(subscription)),
				SonarQubePluginResource.PARAMETER_USER, "sonar-admin");
	}

	@Test
	void createGroupRefused() {
		mockAdmin();
		mockTemplates("");
		mockGroup("ligoj-jupiter-admin", "ligoj-jupiter-admin", false);
		mockWrites();
		httpServer.stubFor(post(urlPathEqualTo("/api/user_groups/create")).willReturn(forbidden()));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(subscription)),
				SonarQubePluginResource.PARAMETER_USER, "sonar-admin");
	}

	@Test
	void createCloud() throws Exception {
		// The node of a SonarQube Cloud organization
		final var pv = new ParameterValue();
		pv.setParameter(em.find(Parameter.class, SonarQubePluginResource.PARAMETER_ORGANIZATION));
		pv.setNode(em.find(Node.class, "service:qa:sonarqube:bpr"));
		pv.setData("my-org");
		em.persist(pv);
		em.flush();
		cacheManager.getCache("subscription-parameters").clear();
		setParameter(SonarQubePluginResource.PARAMETER_ROLES, "{\"ligoj-jupiter-dev\": [\"user\"]}");

		mockAdmin();
		httpServer.stubFor(get(urlEqualTo("/api/components/search?qualifiers=TRK&ps=1&organization=my-org")).willReturn(okJson("{\"components\":[]}")));
		httpServer.stubFor(get(urlEqualTo(SEARCH_TEMPLATE + "&organization=my-org")).willReturn(okJson("{\"permissionTemplates\":[]}")));
		httpServer.stubFor(get(urlEqualTo("/api/user_groups/search?q=ligoj-jupiter-dev&organization=my-org"))
				.willReturn(okJson("{\"groups\":[{\"name\":\"ligoj-jupiter-dev\"}]}")));
		mockWrites();
		httpServer.start();

		resource.create(subscription);
		httpServer.verify(postRequestedFor(urlEqualTo(CREATE_TEMPLATE + "&organization=my-org")));
		httpServer.verify(postRequestedFor(urlEqualTo(
				"/api/permissions/add_group_to_template?templateName=ligoj-jupiter&groupName=ligoj-jupiter-dev&permission=user&organization=my-org")));
	}

	private static final String PROJECTS = "/api/projects/search?ps=500&p=1&q=ligoj-jupiter%3A";

	/**
	 * The latest analyzed project: its measures, no branch.
	 */
	private Map<String, String> statusParameters() {
		final var parameters = new HashMap<>(subscriptionResource.getParameters(subscription));
		parameters.put(SonarQubePluginResource.PARAMETER_MAX_BRANCHES, "1");
		return parameters;
	}

	private void mockMeasures(final String key) {
		httpServer.stubFor(get(urlEqualTo("/api/server/version")).willReturn(ok("26.6.0.123539")));
		httpServer.stubFor(get(urlEqualTo("/api/measures/component?component=" + SonarQubeTemplates.encode(key)
				+ "&metricKeys=" + SonarQubePluginResource.DEFAULT_METRICS_63))
				.willReturn(okJson("{\"component\":{\"key\":\"" + key + "\",\"name\":\"API\",\"measures\":[{\"metric\":\"ncloc\",\"value\":\"120\"}]}}")));
	}

	@SuppressWarnings("unchecked")
	private static List<String> keys(final Map<String, Object> data) {
		return ((List<SonarQubeTemplateProject>) data.get("projects")).stream().map(SonarQubeTemplateProject::key).toList();
	}

	@Test
	void statusTemplate() throws Exception {
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"ligoj-jupiter:.*\"}");
		httpServer.stubFor(get(urlEqualTo(PROJECTS)).willReturn(okJson("""
				{"paging": {"pageIndex": 1, "pageSize": 500, "total": 5}, "components": [
				  {"key": "ligoj-jupiter:web", "name": "Web", "lastAnalysisDate": "2026-10-01T08:00:00+0000"},
				  {"key": "ligoj-jupiter:new", "name": "New"},
				  {"key": "ligoj-jupiter:api", "name": "API", "lastAnalysisDate": "2026-10-10T08:00:00+0200"},
				  {"key": "ligoj-jupiter:bad", "name": "Bad", "lastAnalysisDate": "yesterday"},
				  {"key": "other:ligoj-jupiter:x", "name": "Other", "lastAnalysisDate": "2026-10-12T08:00:00+0000"}]}""")));
		mockMeasures("ligoj-jupiter:api");
		httpServer.start();

		final var data = resource.checkSubscriptionStatus(statusParameters()).getData();
		Assertions.assertEquals(Map.of("name", "ligoj-jupiter", "pattern", "ligoj-jupiter:.*"), data.get("template"));
		// Latest analysis first, the never analyzed and unreadable dates last, by key; the other keys are ignored
		Assertions.assertEquals(List.of("ligoj-jupiter:api", "ligoj-jupiter:web", "ligoj-jupiter:bad", "ligoj-jupiter:new"), keys(data));
		Assertions.assertEquals(4, data.get("projectsCount"));
		final var project = (SonarQubeProject) data.get("project");
		Assertions.assertEquals("ligoj-jupiter:api", project.getKey());
		Assertions.assertEquals(120, project.getMeasuresAsMap().get("ncloc"));
	}

	@Test
	void statusTemplateNotAnalyzed() throws Exception {
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"ligoj-jupiter:.*\"}");
		httpServer.stubFor(get(urlEqualTo(PROJECTS)).willReturn(okJson("""
				{"paging": {"pageIndex": 1, "pageSize": 500, "total": 1}, "components": [{"key": "ligoj-jupiter:new", "name": "New"}]}""")));
		httpServer.start();

		final var data = resource.checkSubscriptionStatus(statusParameters()).getData();
		Assertions.assertEquals(List.of("ligoj-jupiter:new"), keys(data));
		Assertions.assertFalse(data.containsKey("project"));
	}

	@Test
	void statusTemplateMissing() {
		mockTemplates("");
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkSubscriptionStatus(statusParameters())),
				SonarQubePluginResource.PARAMETER_TEMPLATE, "sonar-template");
	}

	@Test
	void statusTemplateWithoutPattern() throws Exception {
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\"}");
		httpServer.start();

		final var data = resource.checkSubscriptionStatus(statusParameters()).getData();
		Assertions.assertEquals(List.of(), keys(data));
		Assertions.assertEquals(0, data.get("projectsCount"));
		httpServer.verify(0, getRequestedFor(urlPathEqualTo("/api/projects/search")));
	}

	@Test
	void statusTemplatePaging() throws Exception {
		// No literal start: no server side filter
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\".*-jupiter:.*\"}");
		httpServer.stubFor(get(urlEqualTo("/api/projects/search?ps=500&p=1")).willReturn(okJson("""
				{"paging": {"pageIndex": 1, "pageSize": 500, "total": 501}, "components": [{"key": "ligoj-jupiter:a", "name": "A"}]}""")));
		httpServer.stubFor(get(urlEqualTo("/api/projects/search?ps=500&p=2")).willReturn(okJson("""
				{"paging": {"pageIndex": 2, "pageSize": 500, "total": 501}, "components": [{"key": "ligoj-jupiter:b", "name": "B"}]}""")));
		httpServer.start();

		Assertions.assertEquals(List.of("ligoj-jupiter:a", "ligoj-jupiter:b"), keys(resource.checkSubscriptionStatus(statusParameters()).getData()));
	}

	private static final String TEMPLATE = "{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"ligoj-jupiter:.*\"}";

	/**
	 * The details of another project of the template, as the dialog of the other projects reads them.
	 */
	@Test
	void getTemplateProject() {
		mockTemplates(TEMPLATE);
		mockMeasures("ligoj-jupiter:web");
		httpServer.stubFor(get(urlPathEqualTo("/api/project_branches/list")).willReturn(okJson("{\"branches\":[]}")));
		httpServer.start();

		final var project = resource.getTemplateProject(subscription, "ligoj-jupiter:web");
		Assertions.assertEquals("ligoj-jupiter:web", project.getKey());
		Assertions.assertEquals(120, project.getMeasuresAsMap().get("ncloc"));
	}

	/**
	 * The node credentials read any project: only the projects of the template are given.
	 */
	@Test
	void getTemplateProjectOutside() {
		mockTemplates(TEMPLATE);
		mockMeasures("other:web");
		httpServer.start();

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.getTemplateProject(subscription, "other:web")),
				SonarQubePluginResource.PARAMETER_PROJECT, "sonar-project");
		httpServer.verify(0, getRequestedFor(urlPathEqualTo("/api/measures/component")));
	}

	/**
	 * The template as SonarQube has it now decides, as for the projects listed by the status.
	 */
	@Test
	void getTemplateProjectLivePattern() {
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"jupiter-v2:.*\"}");
		mockMeasures("jupiter-v2:web");
		httpServer.stubFor(get(urlPathEqualTo("/api/project_branches/list")).willReturn(okJson("{\"branches\":[]}")));
		httpServer.start();

		Assertions.assertEquals("jupiter-v2:web", resource.getTemplateProject(subscription, "jupiter-v2:web").getKey());
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.getTemplateProject(subscription, "ligoj-jupiter:web")),
				SonarQubePluginResource.PARAMETER_PROJECT, "sonar-project");
	}

	@Test
	void getTemplateProjectUnusablePattern() {
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"ligoj-jupiter:(\"}");
		httpServer.start();

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.getTemplateProject(subscription, "ligoj-jupiter:web")),
				SonarQubePluginResource.PARAMETER_PROJECT, "sonar-project");
	}

	@Test
	void getTemplateProjectNotFound() {
		mockTemplates(TEMPLATE);
		httpServer.stubFor(get(urlEqualTo("/api/server/version")).willReturn(ok("26.6.0.123539")));
		httpServer.stubFor(get(urlPathEqualTo("/api/measures/component")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.getTemplateProject(subscription, "ligoj-jupiter:gone")),
				SonarQubePluginResource.PARAMETER_PROJECT, "sonar-project");
	}

	@Test
	void getTemplateProjectTemplateMissing() {
		mockTemplates("");
		httpServer.start();

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.getTemplateProject(subscription, "ligoj-jupiter:web")),
				SonarQubePluginResource.PARAMETER_TEMPLATE, "sonar-template");
	}

	/**
	 * A linked project subscription has no other project.
	 */
	@Test
	void getTemplateProjectLinkSubscription() {
		setParameter(SonarQubePluginResource.PARAMETER_TEMPLATE, null);
		setParameter(SonarQubePluginResource.PARAMETER_PROJECT, "ligoj-jupiter:api");
		httpServer.start();

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.getTemplateProject(subscription, "ligoj-jupiter:web")),
				SonarQubePluginResource.PARAMETER_PROJECT, "sonar-project");
		httpServer.verify(0, anyRequestedFor(anyUrl()));
	}

	/**
	 * A subscription the principal user cannot see gives nothing.
	 */
	@Test
	void getTemplateProjectNotVisible() {
		httpServer.start();
		initSpringSecurityContext("any");

		Assertions.assertThrows(Exception.class, () -> resource.getTemplateProject(subscription, "ligoj-jupiter:web"));
		httpServer.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void literalPrefix() {
		Assertions.assertEquals("ligoj-jupiter:", SonarQubeTemplates.literalPrefix("ligoj-jupiter:.*"));
		Assertions.assertEquals("", SonarQubeTemplates.literalPrefix(".*"));
		Assertions.assertEquals("abc", SonarQubeTemplates.literalPrefix("abc"));
	}

	private static final String DELETE_TEMPLATE = "/api/permissions/delete_template?templateName=ligoj-jupiter";

	@Test
	void deleteNoRemoteData() throws Exception {
		httpServer.start();
		resource.delete(subscription, false);
		httpServer.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void deleteLinkSubscription() throws Exception {
		setParameter(SonarQubePluginResource.PARAMETER_TEMPLATE, null);
		setParameter(SonarQubePluginResource.PARAMETER_PROJECT, "123456");
		httpServer.start();
		resource.delete(subscription, true);
		httpServer.verify(0, anyRequestedFor(anyUrl()));
	}

	@Test
	void deleteTemplate() throws Exception {
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"ligoj-jupiter:.*\"}");
		mockWrites();
		httpServer.start();

		resource.delete(subscription, true);
		httpServer.verify(postRequestedFor(urlEqualTo(DELETE_TEMPLATE)));
		// Groups and projects are kept
		httpServer.verify(1, postRequestedFor(urlPathMatching("/api/.*")));
		Assertions.assertEquals(List.of(), warnings);
	}

	@Test
	void deleteTemplateGone() throws Exception {
		mockTemplates("");
		mockWrites();
		httpServer.start();

		resource.delete(subscription, true);
		httpServer.verify(0, postRequestedFor(urlPathMatching("/api/.*")));
		Assertions.assertEquals(List.of("sonar-template-missing {template=ligoj-jupiter}"), warnings);
	}

	@Test
	void deleteRefused() {
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"ligoj-jupiter:.*\"}");
		httpServer.stubFor(post(urlPathEqualTo("/api/permissions/delete_template")).willReturn(forbidden()));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete(subscription, true)),
				SonarQubePluginResource.PARAMETER_USER, "sonar-admin");
	}

	@Test
	void deleteTemplateUnreadable() {
		// The template search is refused: the template is not reported as already deleted
		httpServer.stubFor(get(urlEqualTo(SEARCH_TEMPLATE)).willReturn(forbidden()));
		mockWrites();
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.delete(subscription, true)),
				SonarQubePluginResource.PARAMETER_USER, "sonar-admin");
		Assertions.assertEquals(List.of(), warnings);
		httpServer.verify(0, postRequestedFor(urlPathMatching("/api/.*")));
	}

	@Test
	void statusTemplateUnreadable() {
		httpServer.stubFor(get(urlEqualTo(SEARCH_TEMPLATE)).willReturn(forbidden()));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkSubscriptionStatus(statusParameters())),
				SonarQubePluginResource.PARAMETER_USER, "sonar-admin");
	}

	@Test
	void statusProjectsRefused() {
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"ligoj-jupiter:.*\"}");
		httpServer.stubFor(get(urlEqualTo(PROJECTS)).willReturn(forbidden()));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.checkSubscriptionStatus(statusParameters())),
				SonarQubePluginResource.PARAMETER_USER, "sonar-admin");
	}

	@Test
	void statusTemplateAlternation() throws Exception {
		// An alternation: no server side filter, else the projects of the other branch would be missing
		mockTemplates("{\"id\":\"T1\",\"name\":\"ligoj-jupiter\",\"projectKeyPattern\":\"ligoj-jupiter:.*|ligoj-saturn:.*\"}");
		httpServer.stubFor(get(urlEqualTo("/api/projects/search?ps=500&p=1")).willReturn(okJson("""
				{"paging": {"pageIndex": 1, "pageSize": 500, "total": 3}, "components": [
				  {"key": "ligoj-jupiter:a", "name": "A"}, {"key": "ligoj-saturn:b", "name": "B"}, {"key": "other:c", "name": "C"}]}""")));
		httpServer.start();

		Assertions.assertEquals(List.of("ligoj-jupiter:a", "ligoj-saturn:b"), keys(resource.checkSubscriptionStatus(statusParameters()).getData()));
	}

	@Test
	void literalPrefixOptional() {
		Assertions.assertEquals("", SonarQubeTemplates.literalPrefix("demo-1:.*|demo-2:.*"));
		Assertions.assertEquals("demo", SonarQubeTemplates.literalPrefix("demos?:.*"));
		Assertions.assertEquals("a", SonarQubeTemplates.literalPrefix("ab*"));
		Assertions.assertEquals("a", SonarQubeTemplates.literalPrefix("ab{0,1}"));
		Assertions.assertEquals("abc", SonarQubeTemplates.literalPrefix("abc+"));
	}

	@Test
	void createRefusedOther() {
		mockAdmin();
		mockTemplates("");
		httpServer.stubFor(post(urlPathEqualTo("/api/permissions/create_template"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_BAD_REQUEST).withBody("{\"errors\":[{\"msg\":\"Name is invalid\"}]}")));
		httpServer.start();
		final var e = Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(subscription));
		MatcherUtil.assertThrows(e, SonarQubePluginResource.PARAMETER_URL, "sonar-error");
		Assertions.assertTrue(e.getErrors().toString().contains("Name is invalid"), e.getErrors().toString());
	}

	@Test
	void createSearchNoAnswer() {
		mockAdmin();
		httpServer.stubFor(get(urlEqualTo(SEARCH_TEMPLATE)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
		mockWrites();
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.create(subscription)),
				SonarQubePluginResource.PARAMETER_URL, "sonar-connection");
		httpServer.verify(0, postRequestedFor(urlPathMatching("/api/.*")));
	}

	@Test
	void createReusedTemplateOtherCase() throws Exception {
		// SonarQube template names are unique whatever their case: the template is reused under its own name
		mockAdmin();
		mockTemplates("{\"id\":\"T1\",\"name\":\"Ligoj-Jupiter\",\"projectKeyPattern\":\"ligoj-jupiter:.*\"}");
		mockGroup("ligoj-jupiter-admin", "ligoj-jupiter-admin", true);
		mockGroup("ligoj-jupiter-dev", "ligoj-jupiter-dev", true);
		mockWrites();
		httpServer.start();

		resource.create(subscription);
		httpServer.verify(0, postRequestedFor(urlPathEqualTo("/api/permissions/create_template")));
		httpServer.verify(postRequestedFor(urlEqualTo(
				"/api/permissions/add_group_to_template?templateName=Ligoj-Jupiter&groupName=ligoj-jupiter-dev&permission=scan")));
		Assertions.assertEquals(List.of("sonar-template-reused {template=Ligoj-Jupiter}"), warnings);
	}

	@Test
	void createModeParameters() {
		final var template = em.find(Parameter.class, SonarQubePluginResource.PARAMETER_TEMPLATE);
		final var pattern = em.find(Parameter.class, SonarQubePluginResource.PARAMETER_TEMPLATE_PATTERN);
		final var roles = em.find(Parameter.class, SonarQubePluginResource.PARAMETER_ROLES);
		Assertions.assertEquals(SubscriptionMode.CREATE, template.getMode());
		Assertions.assertEquals(SubscriptionMode.CREATE, pattern.getMode());
		Assertions.assertEquals(SubscriptionMode.CREATE, roles.getMode());
		Assertions.assertTrue(template.isMandatory());
		Assertions.assertTrue(pattern.isMandatory());
		Assertions.assertFalse(roles.isMandatory());
		// The project is asked to link only
		Assertions.assertEquals(SubscriptionMode.LINK, em.find(Parameter.class, SonarQubePluginResource.PARAMETER_PROJECT).getMode());
	}

	@Test
	void update() {
		// An installation seeded before the CREATE mode
		em.find(Node.class, SonarQubePluginResource.KEY).setMode(SubscriptionMode.LINK);
		em.find(Parameter.class, SonarQubePluginResource.PARAMETER_PROJECT).setMode(SubscriptionMode.ALL);

		resource.update("5.0.2");
		Assertions.assertEquals(SubscriptionMode.ALL, em.find(Node.class, SonarQubePluginResource.KEY).getMode());
		Assertions.assertEquals(SubscriptionMode.LINK, em.find(Parameter.class, SonarQubePluginResource.PARAMETER_PROJECT).getMode());
		// The existing instances keep offering the LINK mode only: the administrator widens one as needed
		Assertions.assertEquals(SubscriptionMode.LINK, em.find(Node.class, "service:qa:sonarqube:bpr").getMode());

		// Idempotent: it runs again on every restart of an IDE-run API
		resource.update("5.0.2");
		Assertions.assertEquals(SubscriptionMode.ALL, em.find(Node.class, SonarQubePluginResource.KEY).getMode());
		Assertions.assertEquals(SubscriptionMode.LINK, em.find(Parameter.class, SonarQubePluginResource.PARAMETER_PROJECT).getMode());
	}
}
