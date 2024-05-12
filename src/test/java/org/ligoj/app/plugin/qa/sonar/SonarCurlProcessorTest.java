/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

/**
 * Test class of {@link SonarCurlProcessor}
 */
class SonarCurlProcessorTest extends AbstractServerTest {


	private void sonarCurlProcessorToken(final String version, final String token, final String auth) {
		// Coverage only
		final var processor = new SonarCurlProcessor(version, Map.of( SonarPluginResource.PARAMETER_USER,token,
				SonarPluginResource.PARAMETER_PASSWORD,""));
		final var request = Mockito.mock(CurlRequest.class);
		final var headers = new HashMap<String, String>();
		Mockito.doReturn(headers).when(request).getHeaders();
		processor.process(request);
		Assertions.assertEquals("Basic "+auth,request.getHeaders().get("Authorization"));
	}

	@Test
	void sonarCurlProcessorTokenGlobal() {
		sonarCurlProcessorToken("9.9.3", "sqa_1234567890123456789012345678901234567890", "c3FhXzEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA6");
	}
	@Test
	void sonarCurlProcessorTokenUser() {
		sonarCurlProcessorToken("9.9.3", "squ_1234567890123456789012345678901234567890", "c3F1XzEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA6");
	}
	@Test
	void sonarCurlProcessorNoCredentials() {
		// Coverage only
		final var processor = new SonarCurlProcessor("9.9.3", new HashMap<>());
		final var request = Mockito.mock(CurlRequest.class);
		final var headers = new HashMap<String, String>();
		Mockito.doReturn(headers).when(request).getHeaders();
		processor.process(request);
		Assertions.assertNull(request.getHeaders().get("Authorization"));
	}
	@Test
	void sonarCurlProcessorTokenUser92() {
		sonarCurlProcessorToken("9.2.1", "1234567890123456789012345678901234567890", "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDo=");
	}
}
