package org.styli.services.customer.pojo.registration.response;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonInclude(Include.NON_NULL)
public class CustomerWishListBody implements Serializable {

	private static final long serialVersionUID = 1505668467350112504L;

	private Integer customerId;

	private Integer wishListId;
	
	private Integer productCount;

	private boolean showPriceDropNotification;

	private String wishListItemId;

	private Object products;

	private List<WishValue> productIds;

	private String deepLink;

	private boolean allOOS;

	private String message;
	
	private Integer totalProductCount;
}
