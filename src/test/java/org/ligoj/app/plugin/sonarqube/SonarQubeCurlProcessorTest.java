/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.sonarqube;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.bootstrap.core.curl.CurlRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Test class of {@link SonarQubeCurlProcessor}
 */
class SonarQubeCurlProcessorTest extends AbstractServerTest {

	private void assertAuthorization(final Map<String, String> parameters, final String expectedCredentials) {
		try (var processor = new SonarQubeCurlProcessor(parameters)) {
			final var request = mock(CurlRequest.class);
			final var headers = new HashMap<String, String>();
			doReturn(headers).when(request).getHeaders();
			processor.process(request);
			Assertions.assertEquals(expectedCredentials == null ? null : "Basic " + Base64.getEncoder().encodeToString(expectedCredentials.getBytes(StandardCharsets.UTF_8)),
					request.getHeaders().get("Authorization"));
		}
	}

	/**
	 * A token typed in the user parameter, with an empty password.
	 */
	private void sonarCurlProcessorToken(final String token) {
		assertAuthorization(Map.of(SonarQubePluginResource.PARAMETER_USER, token, SonarQubePluginResource.PARAMETER_PASSWORD, ""), token + ":");
	}

	/**
	 * A token typed in the password parameter is sent as the login with an empty password: SonarQube rejects a
	 * token sent as the password of a login.
	 */
	private void sonarCurlProcessorPassword(final String password, final String expectedCredentials) {
		assertAuthorization(Map.of(SonarQubePluginResource.PARAMETER_USER, "admin", SonarQubePluginResource.PARAMETER_PASSWORD, password), expectedCredentials);
	}

	@Test
	void sonarCurlProcessorTokenGlobal() {
		sonarCurlProcessorToken("sqa_1234567890123456789012345678901234567890");
	}

	@Test
	void sonarCurlProcessorTokenUser() {
		sonarCurlProcessorToken("squ_1234567890123456789012345678901234567890");
	}

	@Test
	void sonarCurlProcessorTokenUser92() {
		sonarCurlProcessorToken("1234567890123456789012345678901234567890");
	}

	@Test
	void sonarCurlProcessorNoCredentials() {
		assertAuthorization(new HashMap<>(), null);
	}

	@Test
	void sonarCurlProcessorTokensAsPassword() {
		// User, global analysis and project analysis tokens, whatever the SonarQube version
		for (final var token : new String[]{"squ_0123456789abcdef0123456789abcdef01234567", "sqa_0123456789abcdef0123456789abcdef01234567",
				"sqp_0123456789abcdef0123456789abcdef01234567"}) {
			sonarCurlProcessorPassword(token, token + ":");
		}
	}

	@Test
	void sonarCurlProcessorLegacyTokenAsPassword() {
		// A token created before 9.4 has no prefix and stays valid after an upgrade
		sonarCurlProcessorPassword("0123456789abcdef0123456789abcdef01234567", "0123456789abcdef0123456789abcdef01234567:");
	}

	@Test
	void sonarCurlProcessorPassword() {
		sonarCurlProcessorPassword("secret", "admin:secret");
		sonarCurlProcessorPassword("squ_not-a-token", "admin:squ_not-a-token");
	}
}
