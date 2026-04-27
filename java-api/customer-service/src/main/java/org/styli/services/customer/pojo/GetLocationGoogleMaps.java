package org.styli.services.customer.pojo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class GetLocationGoogleMaps {

	private String formattedAddress;
	private String city;
	private String province;
	private String country;
	private String countryShort;
	private String area;
	private BigDecimal latitude;
	private BigDecimal longitude;
	private Boolean serviceable;
	private String region;
	private Integer regionId;
	private Boolean successResponse = false;
	
}