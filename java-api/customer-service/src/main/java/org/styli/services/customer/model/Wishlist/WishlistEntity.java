package org.styli.services.customer.model.Wishlist;

import java.io.Serializable;
import java.util.List;

import javax.persistence.Id;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;

/**
 * Wishlist mongodb collection
 */
@Document(collection = "wishlist")
@Data
public class WishlistEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	private Integer id;

	@Field("wishlist_items")
	private List<WishlistItem> wishListItems;
}