package org.ligoj.app.plugin.qa.sonar;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.curl.HttpResponseCallback;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class AuthCurlProcessor extends CurlProcessor {

	private final String username;
	private final String password;
	private static final Base64 BASE64_CODEC = new Base64(0);

	/**
	 * Full constructor holding credential and callback.
	 *
	 * @param username the user login. Empty or null login are accepted, but no authentication will be used.
	 * @param password the user password or API token. <code>null</code> Password is converted to empty string, and still
	 *                 used when user is not empty.
	 * @param callback Not <code>null</code> {@link org.ligoj.bootstrap.core.curl.HttpResponseCallback} used for each response.
	 */
	public AuthCurlProcessor(final String username, final String password, final HttpResponseCallback callback) {
		super(callback, DEFAULT_CONNECTION_TIMEOUT, DEFAULT_RESPONSE_TIMEOUT,  true, null, null);
		this.username = StringUtils.trimToNull(username);
		this.password = Objects.toString(password);
	}

	/**
	 * Process the given request.
	 */
	@Override
	protected boolean process(final CurlRequest request) {
		addAuthenticationHeader(request);
		return super.process(request);
	}

	/**
	 * Add the basic authentication header.
	 *
	 * @param request The request to complete header.
	 */
	protected void addAuthenticationHeader(final CurlRequest request) {
		// Check the authentication is needed or not
		if (username != null) {

			// Build the Basic authentication header
			final var tmp = username + ':' + password;

			// Use the preempted authentication processor
			request.getHeaders().put(HttpHeaders.AUTHORIZATION,
					"Basic " + BASE64_CODEC.encodeToString(tmp.getBytes(StandardCharsets.UTF_8)));
		}
	}

}