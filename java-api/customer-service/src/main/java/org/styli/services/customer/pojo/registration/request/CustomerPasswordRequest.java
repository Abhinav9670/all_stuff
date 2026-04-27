package org.styli.services.customer.pojo.registration.request;

import lombok.Data;

@Data
public class CustomerPasswordRequest {

	private Integer customerId;

	private String currentPassword;

	private String newPassword;

}
