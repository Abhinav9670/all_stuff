package org.styli.services.customer.jwt.security.jwtsecurity.model;

import lombok.Data;

@Data
public class JwtUserInfo {

	private String email;
	private String code;
	private Integer customerId;
	private Integer storeId;
}
