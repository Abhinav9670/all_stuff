package org.styli.services.customer.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.SystemHealth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposing health check for service monitoring
 */
@RestController
@RequestMapping("/rest/customer")
public class HealthCheckController {

	@Autowired
	private HealthEndpoint healthEndpoint;

	private static final Log LOGGER = LogFactory.getLog(HealthCheckController.class);

	@GetMapping("/health")
	public SystemHealth getHealth() {
		try {
			return (SystemHealth) healthEndpoint.health();
		} catch (Exception e) {
			LOGGER.error("Error in health check. Error : ", e);
		}
		return null;
	}

}
