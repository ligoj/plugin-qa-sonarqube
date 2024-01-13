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
public class SonarCurlProcessorTest extends AbstractServerTest {
	@Test
	void sonarCurlProcessorTokenGlobal() {
		// Coverage only
		final var processor = new SonarCurlProcessor(Map.of( SonarPluginResource.PARAMETER_USER,"sqa_1234567890123456789012345678901234567890",
				SonarPluginResource.PARAMETER_PASSWORD,""));
		final var request = Mockito.mock(CurlRequest.class);
		final var headers = new HashMap<String, String>();
		Mockito.doReturn(headers).when(request).getHeaders();
		processor.process(request);
		Assertions.assertEquals("Basic c3FhXzEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA6",request.getHeaders().get("Authorization"));
	}
	@Test
	void sonarCurlProcessorTokenUser() {
		// Coverage only
		final var processor = new SonarCurlProcessor(Map.of( SonarPluginResource.PARAMETER_USER,"squ_1234567890123456789012345678901234567890",
				SonarPluginResource.PARAMETER_PASSWORD,""));
		final var request = Mockito.mock(CurlRequest.class);
		final var headers = new HashMap<String, String>();
		Mockito.doReturn(headers).when(request).getHeaders();
		processor.process(request);
		Assertions.assertEquals("Basic c3F1XzEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTA6",request.getHeaders().get("Authorization"));
	}
	@Test
	void sonarCurlProcessorNoCredentials() {
		// Coverage only
		final var processor = new SonarCurlProcessor(new HashMap<>());
		final var request = Mockito.mock(CurlRequest.class);
		final var headers = new HashMap<String, String>();
		Mockito.doReturn(headers).when(request).getHeaders();
		processor.process(request);
		Assertions.assertNull(request.getHeaders().get("Authorization"));
	}

}
