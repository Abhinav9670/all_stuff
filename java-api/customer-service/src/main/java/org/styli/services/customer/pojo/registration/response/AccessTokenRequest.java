package org.styli.services.customer.pojo.registration.response;

import lombok.*;

@Data
public class AccessTokenRequest {

	private Integer customerId;
	
	private String refreshToken;
	
	private String source;
	
	private String deviceId;
	
	private String token;
	
}
