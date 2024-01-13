/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import org.ligoj.bootstrap.core.curl.DefaultHttpResponseCallback;
import org.ligoj.bootstrap.core.curl.HttpResponseCallback;
import org.ligoj.bootstrap.core.curl.SessionAuthCurlProcessor;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * SonarQube processor.
 */
public class SonarCurlProcessor extends SessionAuthCurlProcessor {

	/**
	 * Pattern validating one of valid SonarQube global analysis, project analysis or user token.
	 */
	private static final Pattern TOKEN_PATTERN = Pattern.compile("sq[aup]_[a-f0-9]{40}");

	/**
	 * Constructor using parameters set.
	 *
	 * @param parameters the SonarQube parameters.
	 */
	public SonarCurlProcessor(final Map<String, String> parameters) {
		this(parameters, new DefaultHttpResponseCallback());
	}

	/**
	 * Constructor using parameters set and callback.
	 *
	 * @param parameters the SonarQube parameters.
	 * @param callback   Not <code>null</code> {@link HttpResponseCallback} used for each response.
	 */
	public SonarCurlProcessor(final Map<String, String> parameters, final HttpResponseCallback callback) {
		super(getBasicUser(parameters), getBasicPassword(parameters), callback);
	}

	/**
	 * Return true when the given value looks like a SonarQube token.
	 *
	 * @param value The value to test.
	 * @return true when the given value looks like a SonarQube token.
	 */
	private static boolean isToken(final String value) {
		return value != null && TOKEN_PATTERN.matcher(value).matches();
	}

	/**
	 * Return the value for BasicAuthentication's user part depending on the available SonarQube credentials materials.
	 *
	 * @param parameters The available subscription parameters.
	 * @return The value for BasicAuthentication's user part depending on the available SonarQube credentials materials.
	 */
	private static String getBasicUser(final Map<String, String> parameters) {
		final String username = parameters.get(SonarPluginResource.PARAMETER_USER);
		final String password = parameters.get(SonarPluginResource.PARAMETER_PASSWORD);
		return isToken(password) ? password : username;
	}

	/**
	 * Return the value for BasicAuthentication's password part depending on the available SonarQube credentials materials.
	 *
	 * @param parameters The available subscription parameters.
	 * @return The value for BasicAuthentication's password part depending on the available SonarQube credentials materials.
	 */
	private static String getBasicPassword(final Map<String, String> parameters) {
		final String password = parameters.get(SonarPluginResource.PARAMETER_PASSWORD);
		return isToken(password) ? "" : password;
	}
}
