package org.styli.services.customer.model.Wishlist;

import java.io.Serializable;
import java.util.Date;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

import lombok.Data;

/**
 * Wishlist items
 */
@Document
@Data
public class WishlistItem implements Serializable {

	private static final long serialVersionUID = 1L;

	private String sku;

	private Integer productId = 0;

	private String price;
	
	private String specialPrice;

	private double lastPrice;

    private String currency;

	private Double previouslySavedPrice;

	@DateTimeFormat(iso = ISO.DATE_TIME)
	private Date createdOn;

	@DateTimeFormat(iso = ISO.DATE_TIME)
	private Date updatedOn;

	private String source;
	
	private String utmCampaign;
	
	private Integer storeId;
	
	private String wishlistItemId;
}