package org.styli.services.customer.pojo.registration.request;

import javax.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class CustomerQueryReq {

	@NotBlank
	private String useridentifier;

	private LoginType loginType;

	private Integer storeId;
		
	private Boolean isOtpRequired = false;

	private String clientVersion;
	private String source;

}
