package org.styli.services.order.config;

/**
 * Constants for APM configuration
 * @author Chandan Behera
 *
 */
public final class ApmConstants {

	private ApmConstants() {
		throw new IllegalStateException("Instance of this class not allowed.");
	}
	
	public static final String APM_ENABLED = "enabled";
	public static final String APM_SERVICE_NAME = "service_name";
	public static final String APM_SERVICE_URL = "server_urls";
	public static final String APM_ENVIRONMENT = "environment";
	public static final String APM_CAPTURE_BODY = "capture_body";
	public static final String APM_CAPTURE_HEADERS = "capture_headers";
}
