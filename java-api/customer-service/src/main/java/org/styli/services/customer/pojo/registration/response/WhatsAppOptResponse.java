package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

@Data
public class WhatsAppOptResponse {

	private Boolean status;

	private String statusCode;

	private String statusMsg;

	private ErrorType error;

	private WhatsAppOptResBody response;

}
