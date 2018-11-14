package org.ligoj.app.plugin.qa.sonar;

import java.util.Map;

import org.ligoj.bootstrap.core.curl.DefaultHttpResponseCallback;
import org.ligoj.bootstrap.core.curl.HttpResponseCallback;
import org.ligoj.bootstrap.core.curl.SessionAuthCurlProcessor;

/**
 * SonarQube processor.
 */
public class SonarCurlProcessor extends SessionAuthCurlProcessor {

	/**
	 * Constructor using parameters set.
	 * 
	 * @param parameters
	 *            the SonarQube parameters.
	 */
	public SonarCurlProcessor(final Map<String, String> parameters) {
		this(parameters, new DefaultHttpResponseCallback());
	}

	/**
	 * Constructor using parameters set and callback.
	 * 
	 * @param parameters
	 *            the SonarQube parameters.
	 * @param callback
	 *            Not <code>null</code> {@link HttpResponseCallback} used for each response.
	 */
	public SonarCurlProcessor(final Map<String, String> parameters, final HttpResponseCallback callback) {
		super(parameters.get(SonarPluginResource.PARAMETER_USER), parameters.get(SonarPluginResource.PARAMETER_PASSWORD), callback);
	}


}
