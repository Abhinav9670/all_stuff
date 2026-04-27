package org.styli.services.customer.pojo.registration.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class WishValue implements Serializable {

	private String productId;

	private String wishListItemId;
	
}
