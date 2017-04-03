package org.ligoj.app.plugin.qa.sonar;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.MatcherUtil;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.qa.QaResource;
import org.ligoj.app.resource.node.NodeResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test class of {@link SonarPluginResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class SonarPluginResourceTest extends AbstractServerTest {
	@Autowired
	private SonarPluginResource resource;

	@Autowired
	private NodeResource nodeResource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	protected int subscription;

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class },
				StandardCharsets.UTF_8.name());
		this.subscription = getSubscription("gStack");

		// Coverage only
		resource.getKey();
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is
	 * only one subscription for a service.
	 */
	protected int getSubscription(final String project) {
		return getSubscription(project, QaResource.SERVICE_KEY);
	}

	@Test
	public void delete() throws Exception {
		resource.delete(subscription, false);
	}

	@Test
	public void getSonarResourceInvalidUrl() {
		resource.getResource(new HashMap<>(), null);
	}

	@Test
	public void getVersion() throws Exception {
		httpServer.stubFor(
				get(urlEqualTo("/api/server/index?format=json")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/sonar/sonar-server-index.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		final String version = resource.getVersion(subscription);
		Assert.assertEquals("4.3.2", version);
	}

	@Test
	public void getLastVersion() throws Exception {
		final String lastVersion = resource.getLastVersion();
		Assert.assertNotNull(lastVersion);
		Assert.assertTrue(lastVersion.compareTo("5.0") > 0);
	}

	@Test
	public void validateProjectNotFound() throws IOException {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(SonarPluginResource.PARAMETER_PROJECT, "sonar-project"));
		httpServer.stubFor(get(urlMatching(".*")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		final Map<String, String> parameters = nodeResource.getParametersAsMap("service:qa:sonarqube:bpr");
		parameters.put(SonarPluginResource.PARAMETER_PROJECT, "0");
		resource.validateProject(parameters);
	}

	@Test
	public void linkNoProject() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(SonarPluginResource.PARAMETER_PROJECT, "sonar-project"));
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
		httpServer.stubFor(get(urlEqualTo("/provisioning"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(
				get(urlEqualTo("/api/server/index?format=json")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/sonar/sonar-server-index.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.stubFor(get(urlEqualTo("/api/resources?format=json&resource=0&metrics=ncloc,coverage,sqale_rating"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(subscription);

		// Nothing to validate for now...
	}

	@Test
	public void link() throws Exception {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
		httpServer.stubFor(get(urlEqualTo("/provisioning"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(
				get(urlEqualTo("/api/server/index?format=json")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/sonar/sonar-server-index.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.stubFor(
				get(urlEqualTo("/api/resources?format=json&resource=16010&metrics=ncloc,coverage,sqale_rating"))
						.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
								new ClassPathResource("mock-server/sonar/sonar-resource-16010.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		// Attach the SonarQube project identifier
		final Parameter parameter = new Parameter();
		parameter.setId(SonarPluginResource.PARAMETER_PROJECT);
		final Subscription subscription = em.find(Subscription.class, this.subscription);
		final ParameterValue parameterValue = new ParameterValue();
		parameterValue.setParameter(parameter);
		parameterValue.setData("16010");
		parameterValue.setSubscription(subscription);
		em.persist(parameterValue);
		em.flush();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(this.subscription);

		// Nothing to validate for now...
	}

	@Test
	public void validateProject() throws IOException {
		httpServer.stubFor(
				get(urlEqualTo("/api/resources?format=json&resource=16010&metrics=ncloc,coverage,sqale_rating"))
						.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
								new ClassPathResource("mock-server/sonar/sonar-resource-16010.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		final Map<String, String> parameters = nodeResource.getParametersAsMap("service:qa:sonarqube:bpr");
		parameters.put(SonarPluginResource.PARAMETER_PROJECT, "16010");
		final SonarProject project = resource.validateProject(parameters);
		Assert.assertEquals(16010, project.getId().intValue());
		Assert.assertEquals("Company1 - Project1", project.getName());
		Assert.assertEquals("Parent defining top level global configuration of projects.", project.getDescription());
		Assert.assertEquals(8644, project.getMeasures().get("ncloc").intValue());
		Assert.assertEquals(100, project.getMeasures().get("coverage").intValue());
		Assert.assertEquals(1, project.getMeasures().get("sqale_rating").intValue());
	}

	@Test
	public void checkStatus() throws Exception {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
		httpServer.stubFor(get(urlEqualTo("/provisioning"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(
				get(urlEqualTo("/api/server/index?format=json")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/sonar/sonar-server-index.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		final Map<String, String> parameters = subscriptionResource.getParametersNoCheck(subscription);
		parameters.remove(SonarPluginResource.PARAMETER_PROJECT);
		Assert.assertTrue(resource.checkStatus(parameters));
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		httpServer.stubFor(
				get(urlEqualTo("/api/resources?format=json&resource=123456&metrics=ncloc,coverage,sqale_rating"))
						.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
								new ClassPathResource("mock-server/sonar/sonar-resource-16010.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();
		Assert.assertTrue(resource.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription))
				.getStatus().isUp());
	}

	@Test
	public void validateAdminAccess() throws Exception {
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
		httpServer.stubFor(get(urlEqualTo("/provisioning"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("<html></html>")));
		httpServer.stubFor(
				get(urlEqualTo("/api/server/index?format=json")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/sonar/sonar-server-index.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		final String version = resource
				.validateAdminAccess(nodeResource.getParametersAsMap("service:qa:sonarqube:bpr"));
		Assert.assertEquals("4.3.2", version);
	}

	@Test
	public void validateAdminAccessConnectivityFail() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(SonarPluginResource.PARAMETER_URL, "sonar-connection"));
		httpServer.stubFor(
				get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_BAD_GATEWAY)));
		httpServer.start();

		resource.validateAdminAccess(nodeResource.getParametersAsMap("service:qa:sonarqube:bpr"));
	}

	@Test
	public void validateAdminAccessLoginFailOn40x() throws Exception {
		validateAdminAccessLoginFail(HttpStatus.SC_BAD_GATEWAY);
	}

	@Test
	public void validateAdminAccessLoginFailOn30x() throws Exception {
		validateAdminAccessLoginFail(HttpStatus.SC_MOVED_TEMPORARILY);
	}

	private void validateAdminAccessLoginFail(final int status) throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(SonarPluginResource.PARAMETER_USER, "sonar-login"));
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(
				get(urlEqualTo("/api/authentication/validate?format=json")).willReturn(aResponse().withStatus(status)));
		httpServer.start();
		resource.validateAdminAccess(nodeResource.getParametersAsMap("service:qa:sonarqube:bpr"));
	}

	@Test
	public void validateAdminAccessNoRight() throws Exception {
		thrown.expect(ValidationJsonException.class);
		thrown.expect(MatcherUtil.validationMatcher(SonarPluginResource.PARAMETER_USER, "sonar-rights"));
		httpServer.stubFor(get(urlEqualTo("/sessions/new")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		httpServer.stubFor(get(urlEqualTo("/api/authentication/validate?format=json"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody("{\"valid\":true}")));
		httpServer.stubFor(
				get(urlEqualTo("/provisioning")).willReturn(aResponse().withStatus(HttpStatus.SC_BAD_GATEWAY)));
		httpServer.start();
		resource.validateAdminAccess(nodeResource.getParametersAsMap("service:qa:sonarqube:bpr"));
	}

	@Test
	public void findProjectByName() throws Exception {
		httpServer.stubFor(
				get(urlEqualTo("/api/resources?format=json")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/sonar/sonar-resource.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();

		final List<SonarProject> projects = resource.findAllByName("service:qa:sonarqube:bpr", "Com");
		Assert.assertEquals(2, projects.size());
		Assert.assertEquals("Company2 - Project2", projects.get(1).getName());
		Assert.assertEquals("Parent defining top level global configuration of projects.",
				projects.get(0).getDescription());
		Assert.assertEquals("fr.company2:project2", projects.get(1).getKey());
		Assert.assertEquals(67541, projects.get(1).getId().intValue());
	}
}
