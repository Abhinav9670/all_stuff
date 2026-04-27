package org.styli.services.customer.repository.Customer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.styli.services.customer.model.Wishlist.WishlistEntity;

public interface WishlistRepository
		extends MongoRepository<WishlistEntity, Integer> {

}