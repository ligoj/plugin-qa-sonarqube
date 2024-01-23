/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.qa.sonar;

import org.ligoj.bootstrap.core.curl.CurlProcessor;
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
	private static final Pattern TOKEN_PATTERN_94 = Pattern.compile("sq[aup]_[a-f0-9]{40}");
	private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-f0-9]{40}");

	/**
	 * Constructor using parameters set.
	 *
	 * @param version The remote SonarQube version
	 * @param parameters the SonarQube parameters.
	 */
	public SonarCurlProcessor(final String version, final Map<String, String> parameters) {
		this(version, parameters, CurlProcessor.DEFAULT_CALLBACK);
	}

	/**
	 * Constructor using parameters set and callback.
	 *
	 * @param version The remote SonarQube version
	 * @param parameters the SonarQube parameters.
	 * @param callback   Not <code>null</code> {@link HttpResponseCallback} used for each response.
	 */
	public SonarCurlProcessor(final String version, final Map<String, String> parameters, final HttpResponseCallback callback) {
		super(getBasicUser(version, parameters), getBasicPassword(version, parameters), callback);
	}

	private static boolean is93API(final String version) {
		return version != null && version.compareTo("9.3.0") >= 0;
	}

	/**
	 * Return true when the given value looks like a SonarQube token.
	 *
	 * @param version The remote SonarQube version
	 * @param value The value to test.
	 * @return true when the given value looks like a SonarQube token.
	 */
	private static boolean isToken(final String version,final String value) {
		return value != null && (is93API(version) ? TOKEN_PATTERN_94: TOKEN_PATTERN).matcher(value).matches();
	}

	/**
	 * Return the value for BasicAuthentication's user part depending on the available SonarQube credentials materials.
	 *
	 * @param version The remote SonarQube version
	 * @param parameters The available subscription parameters.
	 * @return The value for BasicAuthentication's user part depending on the available SonarQube credentials materials.
	 */
	private static String getBasicUser(final String version, final Map<String, String> parameters) {
		final String username = parameters.get(SonarPluginResource.PARAMETER_USER);
		final String password = parameters.get(SonarPluginResource.PARAMETER_PASSWORD);
		return isToken(version, password) ? password : username;
	}

	/**
	 * Return the value for BasicAuthentication's password part depending on the available SonarQube credentials materials.
	 *
	 * @param version The remote SonarQube version
	 * @param parameters The available subscription parameters.
	 * @return The value for BasicAuthentication's password part depending on the available SonarQube credentials materials.
	 */
	private static String getBasicPassword(final String version,final Map<String, String> parameters) {
		final String password = parameters.get(SonarPluginResource.PARAMETER_PASSWORD);
		return isToken(version, password) ? "" : password;
	}
}
