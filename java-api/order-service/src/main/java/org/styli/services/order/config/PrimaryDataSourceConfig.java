package org.styli.services.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 
 * @author chandanbehera
 *
 */
@Component
@ConfigurationProperties(prefix="spring.datasource")
@Getter
@Setter
public class PrimaryDataSourceConfig {
    private String url;
	private String password;
	private String username;
	private Integer connectionTimeout;
	private Integer minimumIdle;
	private Integer maximumPoolSize;
	private Integer idleTimeout;
}