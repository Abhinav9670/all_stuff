package org.styli.services.customer.repository.in.Wishlist;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.styli.services.customer.model.Wishlist.WishlistEntity;

/**this is for india wishlist **/
public interface WishlistInRepository
		extends MongoRepository<WishlistEntity, Integer> {

}