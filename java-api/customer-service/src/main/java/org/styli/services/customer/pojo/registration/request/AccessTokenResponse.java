package org.styli.services.customer.pojo.registration.request;

import lombok.Data;
import org.styli.services.customer.pojo.registration.response.CustomerV4Response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Data
public class AccessTokenResponse implements Serializable {
	private static final long serialVersionUID = -7494610692914103613L;

	private boolean status;

	private String statusCode;

	private String message;
	private String refreshToken;
	
	private CustomerV4Response response;
	
}
