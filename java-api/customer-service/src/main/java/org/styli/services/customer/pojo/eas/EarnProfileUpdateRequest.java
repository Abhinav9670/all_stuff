package org.styli.services.customer.pojo.eas;

import lombok.Data;

@Data
public class EarnProfileUpdateRequest {

	private Integer customerId;
	private Integer storeId;
	private Integer stage;
	private String dob;
	private Boolean isVerifyMobileNumber;
}
