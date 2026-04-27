package org.styli.services.order.pojo.response;

import lombok.Data;

@Data
public class AddressMapperResponse {

	private Boolean status;
	private String statusCode;
	private String statusMsg;
	private AddressMapperCity response;
}
