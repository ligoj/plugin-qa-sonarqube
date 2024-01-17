/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import jakarta.transaction.Transactional;
import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.model.*;
import org.ligoj.app.plugin.qa.QaResource;
import org.ligoj.app.resource.node.ParameterValueResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Test class of {@link SonarPluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class SonarPluginResourceTest extends AbstractServerTest {
	@Autowired
	private SonarPluginResource resource;

	@Autowired
	private ParameterValueResource pvResource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	private int subscription;

	@BeforeEach
	void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv", new Class<?>[]{Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class},
				StandardCharsets.UTF_8);
		this.subscription = getSubscription("Jupiter");
	}

	private void mockVersion() throws IOException {
		mockVersion("");
	}

	private void mockVersion63() throws IOException {
		mockVersion("-6.3");
	}

	private void mockVersion66() throws IOException {
		mockVersion("-6.6");
	}

	private void mockVersion(String version) throws IOException {
		httpServer.stubFor(get(urlEqualTo("/api/server/version"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-server-version" + version + ".txt").getInputStream(), StandardCharsets.UTF_8))));
	}

	private void mockSession() {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
	}

	@Test
	void getKey() {
		// Coverage only
		Assertions.assertEquals("service:qa:sonarqube", resource.getKey());
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is
	 * only one subscription for a service.
	 */
	private int getSubscription(final String project) {
		return getSubscription(project, QaResource.SERVICE_KEY);
	}

	@Test
	void delete() throws Exception {
		resource.delete(subscription, false);
	}

	@Test
	void getSonarResourceInvalidUrl() {
		resource.getResource("9.9.3", new HashMap<>(), null);
	}

	@Test
	void getVersion() throws Exception {
		mockVersion();
		httpServer.start();

		final var version = resource.getVersion(subscription);
		Assertions.assertEquals("4.3.2", version);
	}

	@Test
	void getLastVersion() throws Exception {
		final var lastVersion = resource.getLastVersion();
		Assertions.assertNotNull(lastVersion);
		Assertions.assertTrue(lastVersion.compareTo("5.0") > 0);
	}

	@Test
	void validateProjectNotFound() throws IOException {
		mockVersion();
		httpServer.stubFor(get(urlMatching(".*")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		final var parameters = pvResource.getNodeParameters("service:qa:sonarqube:bpr");
		parameters.put(SonarPluginResource.PARAMETER_PROJECT, "0");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.validateProject(parameters)), SonarPluginResource.PARAMETER_PROJECT, "sonar-project");
	}

	@Test
	void linkNoProject() throws Exception {
		mockVersion();
		mockSession();
		httpServer.stubFor(get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(get(urlEqualTo("/api/resources?format=json&resource=0&metrics=ncloc,coverage,sqale_rating"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.link(subscription)),
				SonarPluginResource.PARAMETER_PROJECT, "sonar-project");
	}

	@Test
	void link() throws Exception {
		mockVersion();
		mockSession();
		httpServer.stubFor(get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(get(urlEqualTo("/api/resources?format=json&resource=16010&metrics=ncloc,coverage,sqale_rating"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource-16010.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
		validateLink("16010");
	}


	@Test
	void link63() throws Exception {
		mockVersion63();
		mockSession();
		httpServer.stubFor(get(urlEqualTo("/api/projects/search")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(get(urlEqualTo("/api/measures/component?component=fr.company1%3Aproject1&metricKeys=ncloc,coverage,sqale_rating,security_rating,reliability_rating,security_review_rating"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource-16010_6.3.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
		validateLink("fr.company1:project1");
	}

	private void validateLink(final String id) throws Exception {
		// Attach the SonarQube project identifier
		final var parameter = new Parameter();
		parameter.setId(SonarPluginResource.PARAMETER_PROJECT);
		final var previous = em.find(Subscription.class, this.subscription);
		final var subscription = new Subscription();
		subscription.setNode(previous.getNode());
		subscription.setProject(previous.getProject());
		em.persist(subscription);
		final var parameterValue = new ParameterValue();
		parameterValue.setParameter(parameter);
		parameterValue.setData(id);
		parameterValue.setSubscription(subscription);
		em.persist(parameterValue);
		em.flush();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(subscription.getId());
	}

	@Test
	void validateProject() throws IOException {
		mockVersion();
		httpServer.stubFor(get(urlEqualTo("/api/resources?format=json&resource=16010&metrics=ncloc,coverage,sqale_rating"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource-16010.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
		final var project = validateProject("16010", null, null);
		Assertions.assertEquals("16010", project.getId());
	}

	@Test
	void validateProject63() throws IOException {
		mockVersion63();
		httpServer.stubFor(get(urlEqualTo("/api/measures/component?component=fr.company1%3Aproject1&metricKeys=ncloc,coverage,sqale_rating,security_rating,reliability_rating,security_review_rating"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource-16010_6.3.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
		final var project = validateProject("fr.company1:project1", null, null);
		Assertions.assertEquals("fr.company1:project1", project.getKey());
	}


	@Test
	void validateProject66() throws Exception {
		mockVersion66();
		mockSession();
		httpServer.stubFor(get(urlEqualTo("/api/measures/component?component=fr.company1%3Aproject1&metricKeys=ncloc,coverage"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource-16010_6.3.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.stubFor(get(urlEqualTo("/api/project_branches/list?project=fr.company1%3Aproject1"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-branches.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.stubFor(get(urlEqualTo("/api/measures/component?component=fr.company1%3Aproject1&metricKeys=ncloc,coverage,sqale_rating,security_rating,reliability_rating,security_review_rating&branch=features%2F1"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-branch-metrics.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.stubFor(get(urlEqualTo("/api/measures/component?component=fr.company1%3Aproject1&metricKeys=ncloc,coverage,sqale_rating,security_rating,reliability_rating,security_review_rating&branch=main"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html/>")));
		httpServer.start();
		final var project = validateProject("fr.company1:project1", "3", "ncloc,coverage");
		Assertions.assertEquals("fr.company1:project1", project.getKey());

		// Check branches size and ordering
		Assertions.assertEquals(3, project.getBranches().size());
		final var mainBranch = project.getBranches().getFirst();
		Assertions.assertTrue(mainBranch.isMain());
		Assertions.assertEquals("main", mainBranch.getName());
		Assertions.assertEquals("2023-08-08T10:11:18+0000", mainBranch.getAnalysisDate());
		Assertions.assertEquals("BRANCH", mainBranch.getType());
		Assertions.assertEquals("OK", mainBranch.getStatus().get("qualityGateStatus"));
		Assertions.assertNull(mainBranch.getMeasuresAsMap());

		 var nextBranch = project.getBranches().get(1);
		Assertions.assertFalse(nextBranch.isMain());
		Assertions.assertEquals("features/1", nextBranch.getName());
		Assertions.assertEquals("2023-08-08T17:12:31+0000", nextBranch.getAnalysisDate());
		Assertions.assertEquals("BRANCH", nextBranch.getType());
		Assertions.assertEquals(5, nextBranch.getMeasuresAsMap().get("security_review_rating"));

		nextBranch = project.getBranches().get(2);
		Assertions.assertFalse(nextBranch.isMain());
		Assertions.assertEquals("pr/34", nextBranch.getName());
		Assertions.assertEquals("2023-08-08T16:12:31+0000", nextBranch.getAnalysisDate());
		Assertions.assertEquals("PULL_REQUEST", nextBranch.getType());
		Assertions.assertEquals("main", nextBranch.getTargetBranchName());
		Assertions.assertEquals("34", nextBranch.getPullRequestKey());
		Assertions.assertNull(nextBranch.getMeasuresAsMap());

	}

	private SonarProject validateProject(final String id, final String maxBranches, final String metrics) throws IOException {
		final var parameters = pvResource.getNodeParameters("service:qa:sonarqube:bpr");
		parameters.put(SonarPluginResource.PARAMETER_PROJECT, id);
		if (maxBranches == null) {
			parameters.remove(SonarPluginResource.PARAMETER_MAX_BRANCHES);
		} else {
			parameters.put(SonarPluginResource.PARAMETER_MAX_BRANCHES, maxBranches);
		}
		if (metrics == null) {
			parameters.remove(SonarPluginResource.PARAMETER_METRICS_OVERRIDE);
		} else {
			parameters.put(SonarPluginResource.PARAMETER_METRICS_OVERRIDE, metrics);
		}
		final var project = resource.validateProject(parameters);
		Assertions.assertEquals("Company1 - Project1", project.getName());
		Assertions.assertEquals("Parent defining top level global configuration of projects.", project.getDescription());
		Assertions.assertEquals(8644, project.getMeasuresAsMap().get("ncloc").intValue());
		Assertions.assertEquals(100, project.getMeasuresAsMap().get("coverage").intValue());
		Assertions.assertEquals(1, project.getMeasuresAsMap().get("sqale_rating").intValue());
		return project;
	}

	@Test
	void checkStatus() throws Exception {
		mockVersion();
		mockSession();
		httpServer.stubFor(get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.start();

		final var parameters = subscriptionResource.getParametersNoCheck(subscription);
		parameters.remove(SonarPluginResource.PARAMETER_PROJECT);
		Assertions.assertTrue(resource.checkStatus(parameters));
	}

	@Test
	void checkSubscriptionStatus() throws Exception {
		mockVersion();
		httpServer.stubFor(get(urlEqualTo("/api/resources?format=json&resource=123456&metrics=ncloc,coverage,sqale_rating"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource-16010.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
		Assertions.assertTrue(resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription)).getStatus().isUp());
	}

	@Test
	void validateAdminAccess() throws Exception {
		mockVersion();
		mockSession();
		httpServer.stubFor(get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.start();

		final var version = resource.validateAdminAccess(pvResource.getNodeParameters("service:qa:sonarqube:bpr"));
		Assertions.assertEquals("4.3.2", version);
	}

	@Test
	void validateAdminAccess63() throws Exception {
		mockVersion63();
		mockSession();
		httpServer.stubFor(get(urlEqualTo("/api/projects/search")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{}")));
		httpServer.start();

		final var version = resource.validateAdminAccess(pvResource.getNodeParameters("service:qa:sonarqube:bpr"));
		Assertions.assertEquals("6.3.0.65466", version);
	}

	@Test
	void validateAdminAccessConnectivityFail() {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_BAD_GATEWAY)));
		httpServer.start();

		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class,
						() -> resource.validateAdminAccess(pvResource.getNodeParameters("service:qa:sonarqube:bpr"))),
				SonarPluginResource.PARAMETER_URL, "sonar-connection");
	}

	@Test
	void validateAdminAccessLoginFailOn40x() {
		validateAdminAccessLoginFail(HttpStatus.SC_BAD_GATEWAY);
	}

	@Test
	void validateAdminAccessLoginFailOn30x() {
		validateAdminAccessLoginFail(HttpStatus.SC_MOVED_TEMPORARILY);
	}

	private void validateAdminAccessLoginFail(final int status) {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json")).willReturn(aResponse().withStatus(status)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () ->
						resource.validateAdminAccess(pvResource.getNodeParameters("service:qa:sonarqube:bpr"))),
				SonarPluginResource.PARAMETER_USER, "sonar-login");
	}

	@Test
	void validateAdminAccessNoRight() throws IOException {
		mockVersion();
		mockSession();
		httpServer.stubFor(get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_BAD_GATEWAY)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () ->
						resource.validateAdminAccess(pvResource.getNodeParameters("service:qa:sonarqube:bpr"))),
				SonarPluginResource.PARAMETER_USER, "sonar-rights");
	}

	@Test
	void validateAdminAccessNoRight63() throws IOException {
		mockVersion63();
		mockSession();
		httpServer.stubFor(get(urlEqualTo("/api/projects/search")).willReturn(aResponse().withStatus(HttpStatus.SC_BAD_GATEWAY)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () ->
						resource.validateAdminAccess(pvResource.getNodeParameters("service:qa:sonarqube:bpr"))),
				SonarPluginResource.PARAMETER_USER, "sonar-rights");
	}

	@Test
	void findProjectByName() throws Exception {
		mockVersion();
		httpServer.stubFor(
				get(urlEqualTo("/api/resources?format=json")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();

		final var projects = resource.findAllByName("service:qa:sonarqube:bpr", "Com");
		Assertions.assertEquals(2, projects.size());
		Assertions.assertEquals("Some1 - Project1", projects.get(1).getName());
		Assertions.assertEquals("Parent defining top level global configuration of projects.", projects.get(0).getDescription());
		Assertions.assertEquals("fr.company1:project1", projects.get(1).getKey());
		Assertions.assertEquals("16010", projects.get(1).getId());
	}


	@Test
	void findProjectByName63() throws Exception {
		mockVersion63();
		httpServer.stubFor(
				get(urlEqualTo("/api/projects/search?q=COM")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-projects.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();

		final var projects = resource.findAllByName("service:qa:sonarqube:bpr", "Com");
		Assertions.assertEquals(2, projects.size());
		Assertions.assertEquals("Some1 - Project1", projects.get(1).getName());
		Assertions.assertEquals("Parent defining top level global configuration of projects.", projects.get(0).getDescription());
		Assertions.assertEquals("fr.company1:project1", projects.get(1).getKey());
	}
}
