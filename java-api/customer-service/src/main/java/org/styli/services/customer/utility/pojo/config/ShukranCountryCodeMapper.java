package org.styli.services.customer.utility.pojo.config;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ShukranCountryCodeMapper implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3422937313904494229L;
	private String country;
	private String code;
}