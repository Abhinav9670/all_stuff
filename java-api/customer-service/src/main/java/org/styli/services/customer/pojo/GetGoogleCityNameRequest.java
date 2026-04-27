package org.styli.services.customer.pojo;

import lombok.Data;

@Data
public class GetGoogleCityNameRequest {

	private String googleCityName;
	private Integer storeId;
}