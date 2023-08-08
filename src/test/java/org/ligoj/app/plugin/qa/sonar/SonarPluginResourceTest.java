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
import java.util.Map;

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
		persistEntities("csv", new Class[]{Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class},
				StandardCharsets.UTF_8);
		this.subscription = getSubscription("gStack");

		// Coverage only
		resource.getKey();
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
		resource.getResource(new HashMap<>(), null);
	}

	@Test
	void getVersion() throws Exception {
		httpServer.stubFor(get(urlEqualTo("/api/server/index?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-server-index.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();

		final String version = resource.getVersion(subscription);
		Assertions.assertEquals("4.3.2", version);
	}

	@Test
	void getLastVersion() throws Exception {
		final var lastVersion = resource.getLastVersion();
		Assertions.assertNotNull(lastVersion);
		Assertions.assertTrue(lastVersion.compareTo("5.0") > 0);
	}

	@Test
	void validateProjectNotFound() {
		httpServer.stubFor(get(urlMatching(".*")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		final var parameters = pvResource.getNodeParameters("service:qa:sonarqube:bpr");
		parameters.put(SonarPluginResource.PARAMETER_PROJECT, "0");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> resource.validateProject(parameters)), SonarPluginResource.PARAMETER_PROJECT, "sonar-project");
	}

	@Test
	void linkNoProject() throws Exception {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
		httpServer.stubFor(get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(get(urlEqualTo("/api/server/index?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-server-index.json").getInputStream(), StandardCharsets.UTF_8))));
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
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
		httpServer.stubFor(get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(get(urlEqualTo("/api/server/index?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-server-index.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.stubFor(get(urlEqualTo("/api/resources?format=json&resource=16010&metrics=ncloc,coverage,sqale_rating"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource-16010.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();

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
		parameterValue.setData("16010");
		parameterValue.setSubscription(subscription);
		em.persist(parameterValue);
		em.flush();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(subscription.getId());

		// Nothing to validate for now...
	}

	@Test
	void validateProject() throws IOException {
		httpServer.stubFor(get(urlEqualTo("/api/resources?format=json&resource=16010&metrics=ncloc,coverage,sqale_rating"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource-16010.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();

		final var parameters = pvResource.getNodeParameters("service:qa:sonarqube:bpr");
		parameters.put(SonarPluginResource.PARAMETER_PROJECT, "16010");
		final var project = resource.validateProject(parameters);
		Assertions.assertEquals(16010, project.getId().intValue());
		Assertions.assertEquals("Company1 - Project1", project.getName());
		Assertions.assertEquals("Parent defining top level global configuration of projects.", project.getDescription());
		Assertions.assertEquals(8644, project.getMeasures().get("ncloc").intValue());
		Assertions.assertEquals(100, project.getMeasures().get("coverage").intValue());
		Assertions.assertEquals(1, project.getMeasures().get("sqale_rating").intValue());
	}

	@Test
	void checkStatus() throws Exception {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
		httpServer.stubFor(get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(get(urlEqualTo("/api/server/index?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-server-index.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();

		final Map<String, String> parameters = subscriptionResource.getParametersNoCheck(subscription);
		parameters.remove(SonarPluginResource.PARAMETER_PROJECT);
		Assertions.assertTrue(resource.checkStatus(parameters));
	}

	@Test
	void checkSubscriptionStatus() throws Exception {
		httpServer.stubFor(get(urlEqualTo("/api/resources?format=json&resource=123456&metrics=ncloc,coverage,sqale_rating"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource-16010.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();
		Assertions.assertTrue(resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription)).getStatus().isUp());
	}

	@Test
	void validateAdminAccess() throws Exception {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
		httpServer.stubFor(get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(get(urlEqualTo("/api/server/index?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-server-index.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();

		final String version = resource.validateAdminAccess(pvResource.getNodeParameters("service:qa:sonarqube:bpr"));
		Assertions.assertEquals("4.3.2", version);
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
	void validateAdminAccessNoRight() {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
		httpServer.stubFor(get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_BAD_GATEWAY)));
		httpServer.start();
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () ->
						resource.validateAdminAccess(pvResource.getNodeParameters("service:qa:sonarqube:bpr"))),
				SonarPluginResource.PARAMETER_USER, "sonar-rights");
	}

	@Test
	void findProjectByName() throws Exception {
		httpServer.stubFor(
				get(urlEqualTo("/api/resources?format=json")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/sonar/sonar-resource.json").getInputStream(), StandardCharsets.UTF_8))));
		httpServer.start();

		final var projects = resource.findAllByName("service:qa:sonarqube:bpr", "Com");
		Assertions.assertEquals(2, projects.size());
		Assertions.assertEquals("Some1 - Project1", projects.get(1).getName());
		Assertions.assertEquals("Parent defining top level global configuration of projects.", projects.get(0).getDescription());
		Assertions.assertEquals("fr.company1:project1", projects.get(1).getKey());
		Assertions.assertEquals(16010, projects.get(1).getId().intValue());
	}
}
