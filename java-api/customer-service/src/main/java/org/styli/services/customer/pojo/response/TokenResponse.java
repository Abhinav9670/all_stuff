package org.styli.services.customer.pojo.response;

import lombok.Data;

@Data
public class TokenResponse {

	private String access_token;
	private String token_type;
	private Long expires_in;
	private String refresh_token;
	private String id_token;
}
