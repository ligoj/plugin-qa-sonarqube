package org.ligoj.app.plugin.qa.sonar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.transaction.Transactional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.dao.SubscriptionRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.qa.QaResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test class of {@link QaResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class QaResourceTest extends AbstractAppTest {

	@Autowired
	private QaResource resource;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

	@Autowired
	private NodeRepository nodeRepository;

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class, },
				StandardCharsets.UTF_8.name());

		// Coverage only
		resource.getKey();
	}

	@Test
	public void delete() throws Exception {
		final Project project = new Project();
		project.setName("TEST");
		project.setPkey("test");
		em.persist(project);

		final Subscription subscription = new Subscription();
		subscription.setProject(project);
		subscription.setNode(nodeRepository.findOneExpected("service:qa"));
		em.persist(subscription);

		Assert.assertEquals(1, subscriptionRepository.findAllByProject(project.getId()).size());
		em.flush();
		em.clear();

		resource.delete(subscription.getId(), false);
		subscriptionRepository.delete(subscription);
		em.flush();
		em.clear();
		Assert.assertEquals(0, subscriptionRepository.findAllByProject(project.getId()).size());
	}

	@Test
	public void create() throws Exception {
		final Project project = new Project();
		project.setName("TEST");
		project.setPkey("test");
		em.persist(project);
		em.flush();

		final Subscription subscription = new Subscription();
		subscription.setProject(project);
		subscription.setNode(nodeRepository.findOneExpected("service:qa"));
		em.persist(subscription);
		em.flush();
		em.clear();

		resource.create(subscription.getId());
		em.flush();
		em.clear();

		em.flush();
		em.clear();
		Assert.assertEquals(1, subscriptionRepository.findAllByProject(project.getId()).size());
	}
}
