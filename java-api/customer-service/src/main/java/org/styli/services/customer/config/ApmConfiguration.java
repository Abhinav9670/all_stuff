package org.styli.services.customer.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import co.elastic.apm.attach.ElasticApmAttacher;

/**
 * APM Configuration
 * 
 * @author Chandan Behera
 *
 */
@Configuration
public class ApmConfiguration {

	@Value("${apm.service_url}")
	private String apmServiceUrl;

	@Value("${env}")
	private String env;
	
	@Value("${region}")
	private String region;

	@PostConstruct
	public void init() {
		ElasticApmAttacher.attach(apmConfig());
	}

	private Map<String, String> apmConfig() {
		Map<String, String> configMap = new LinkedHashMap<>();
		configMap.put(ApmConstants.APM_ENABLED, "true");
		configMap.put(ApmConstants.APM_SERVICE_URL, apmServiceUrl);
		if (Objects.nonNull(region) && "in".equalsIgnoreCase(region))
			configMap.put(ApmConstants.APM_SERVICE_NAME, region.toLowerCase() + "-" + env + "-customer-service");
		else
			configMap.put(ApmConstants.APM_SERVICE_NAME, env + "-customer-service");
		configMap.put(ApmConstants.APM_ENVIRONMENT, env);
		configMap.put(ApmConstants.APM_CAPTURE_BODY, "all");
		configMap.put(ApmConstants.APM_CAPTURE_HEADERS, "true");
		return configMap;
	}
}
