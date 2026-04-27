package org.styli.services.customer.pojo.registration.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class CustomerWishlistV5Request {

	@NotNull
	@Min(1)
	private Integer customerId;

	@NotNull
	@Min(1)
	private Integer storeId;

	private Integer pageSize;
	private Integer pageOffset;
	private Boolean enableQuantity;

	private Boolean showPriceDropNotification=false;
	// Send cityId to get shipmentMode
	private String cityId;
}