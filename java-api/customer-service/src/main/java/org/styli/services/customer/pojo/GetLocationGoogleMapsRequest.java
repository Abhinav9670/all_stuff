package org.styli.services.customer.pojo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class GetLocationGoogleMapsRequest {

	private BigDecimal latitude;
	private BigDecimal longitude;
	private String placeId;
	private BigDecimal prevLatitude;
	private BigDecimal prevLongitude;
	private Integer storeId;
}