package org.styli.services.customer.utility.pojo.config;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ShukranNextTier implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3422937313904494229L;
	private String classic; // If classic next tier will be sivler
	private String silver; // If classic next tier will be gold
	private String gold; // If classic next tier will be platinum
}