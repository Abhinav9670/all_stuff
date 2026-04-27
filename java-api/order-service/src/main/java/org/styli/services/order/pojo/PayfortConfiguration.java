package org.styli.services.order.pojo;

import lombok.Data;

@Data
public class PayfortConfiguration {

	
	private String signatureHash;
	
	private String merchantIdentifier;
	
	private String accessCode;
	
	private String language;
	
	private Integer multiplier;
}
