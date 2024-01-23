package org.ligoj.app.plugin.qa.sonar;

import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.curl.HttpResponseCallback;

/**
 * Basic authenticated CURL processor where credentials are only sent one in order to get a session cookie.
 */
public class SessionAuthCurlProcessor extends AuthCurlProcessor {

	/**
	 * Full constructor holding credential and callback.
	 * 
	 * @param username
	 *            the user login.
	 * @param password
	 *            the user password or API token.
	 * @param callback
	 *            Not <code>null</code> {@link org.ligoj.bootstrap.core.curl.HttpResponseCallback} used for each response.
	 */
	public SessionAuthCurlProcessor(final String username, final String password, final HttpResponseCallback callback) {
		super(username, password, callback);
	}

	/**
	 * Manage authentication only for the first request.
	 */
	@Override
	protected void addAuthenticationHeader(final CurlRequest request) {
		if (request.getCounter() == 0) {
			// Manage authentication only for the first request.
			super.addAuthenticationHeader(request);
		}
	}

}