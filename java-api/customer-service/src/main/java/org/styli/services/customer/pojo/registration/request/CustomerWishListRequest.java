package org.styli.services.customer.pojo.registration.request;

import java.util.List;

import javax.validation.constraints.NotBlank;

import lombok.Data;

@Data
public class CustomerWishListRequest {

	private List<WishProduct> wishList;
	//@NotBlank
	private Integer customerId;
	//@NotBlank
	private Integer storeId;
	
}
