package org.styli.services.order.pojo.config;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppEnvironments implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2647626960698729660L;
	private String type;
	private boolean updateRequired;
	private String version;
	private String lastUpdatedVersion;
}
