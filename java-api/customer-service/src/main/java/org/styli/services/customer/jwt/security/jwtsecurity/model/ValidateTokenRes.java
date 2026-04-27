package org.styli.services.customer.jwt.security.jwtsecurity.model;

import lombok.Data;

@Data
public class ValidateTokenRes {

	private Boolean status;
    private String statusCode;
    private String statusMsg;
}
