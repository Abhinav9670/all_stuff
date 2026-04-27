package org.styli.services.order.pojo.request;

import lombok.Data;

@Data
public class CitySearchAddressMapperRequest {

	private String country;
	private String citySearchKey;
	private String regionId;
}
