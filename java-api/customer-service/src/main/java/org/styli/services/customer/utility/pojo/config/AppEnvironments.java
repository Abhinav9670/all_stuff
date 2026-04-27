package org.styli.services.customer.utility.pojo.config;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
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
