package org.styli.services.customer.pojo.registration.request;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Logout Response
 */
@Getter
@Setter
@Builder
public class LogoutResponse {

	private boolean status;

	private String message;

}
