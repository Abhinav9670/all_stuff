package org.styli.services.customer.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.model.CustomerGridFlat;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.model.Wishlist.WishlistItem;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;

import com.algolia.search.SearchClient;

@Service
public interface Client {

    CustomerEntity findByEmail(String email);

    CustomerEntity findByEntityId(Integer entityId);

    CustomerAddressEntity findAddressByEntityId(Integer addressdId);

    Map<Integer, String> getAttrMap();

    CustomerEntity saveAndFlushCustomerEntity(CustomerEntity customerEntity);

    CustomerGridFlat saveAndFlushCustomerGrid(CustomerGridFlat customerGridFlat);

    CustomerAddressEntity saveAndFlushAddressEntity(CustomerAddressEntity addressEntity);

    String resetPassword(String email, Integer storeId, String magentoBaseUrl)
            throws CustomerException;

    Boolean exitsById(Integer customerId);

    WishlistEntity findByCustomerId(Integer customerId);

    Store findByStoreId(Integer storeId);


    WishlistEntity saveandFlushWishlistEntity(WishlistEntity entity);

    List<Integer> findByWebSiteId(Integer websiteId);


    List<Stores> getStoresArray();

    SearchClient getAlgoliaClient();

	CustomerEntity findByPhoneNumber(String phoneNumber);
	
	CustomerEntity findByCardNumber(String cardNumber);
	
	CustomerEntity saveAndFlushMongoCustomerDocument(CustomerEntity customerEntity);
	
	public List<WishlistItem> getWishlistItems(Integer wishlistId , Integer storeId);

}
