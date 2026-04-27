package org.styli.services.customer.utility.pojo.config;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class ShukranQualifingPurchase implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3422937313904494229L;
	private Integer silver;
	private Integer gold;
	private Integer classic;
	private Integer platinum;
}