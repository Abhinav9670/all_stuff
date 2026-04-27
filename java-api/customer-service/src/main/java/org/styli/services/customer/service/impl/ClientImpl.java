package org.styli.services.customer.service.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.styli.services.customer.exception.CustomerException;
import org.styli.services.customer.helper.WishlistHelperV5;
import org.styli.services.customer.model.CustomerGridFlat;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.model.Wishlist.WishlistItem;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.address.response.CustomerAddressEntity;
import org.styli.services.customer.pojo.registration.response.CustomerEntity;
import org.styli.services.customer.repository.StaticComponents;
import org.styli.services.customer.repository.StoreRepository;
import org.styli.services.customer.repository.Customer.CustomerAddressEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.repository.Customer.CustomerGridFlatRepository;
import org.styli.services.customer.repository.Customer.WishlistRepository;
import org.styli.services.customer.repository.in.Wishlist.WishlistInRepository;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import com.algolia.search.SearchClient;

@Service
public class ClientImpl implements Client {

    private static final Log LOGGER = LogFactory.getLog(ClientImpl.class);

    @Autowired
    CustomerEntityRepository customerEntityRepository;

    @Autowired
    CustomerGridFlatRepository customerGridFlatRepository;

    @Autowired
    CustomerAddressEntityRepository customerAddressEntityRepository;

    @Autowired
    StaticComponents staticComponents;

    @Autowired
    CustomRestTemplate customRestTemplate;

    @Autowired
    WishlistRepository wishlistRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    WishlistHelperV5 wishlistHelper;
    
	@Value("${region}")
	private String region;
	
	@Autowired
	WishlistInRepository wishlistInRepository;
	
	@Autowired
	private MongoTemplate mongoTemplate;



    @Override
    public CustomerEntity findByEmail(final String email) {
    	Query query = new Query();
    	// query.addCriteria(Criteria.where("email").regex(email, "i"))
    	Collation collation = Collation.of("en").strength(Collation.ComparisonLevel.secondary());
    	query.addCriteria(Criteria.where("email").is(email)).collation(collation);    	
    	List<CustomerEntity> res = mongoTemplate.find(query, CustomerEntity.class);
    	if(!res.isEmpty())
    		return res.get(0);
    	else return null;
        // return customerEntityRepository.findByEmailIgnoreCase(email.toLowerCase())
    }

    @Override
    public CustomerEntity findByEntityId(final Integer entityId) {

        return customerEntityRepository.findByEntityId(entityId);
    }

    @Override
    public Map<Integer, String> getAttrMap() {

        return staticComponents.getAttrMap();
    }

    @Override
    public CustomerEntity saveAndFlushCustomerEntity(final CustomerEntity customerEntity) {
        return customerEntityRepository.save(customerEntity);
    }

    @Override
    public CustomerGridFlat saveAndFlushCustomerGrid(final CustomerGridFlat customerGridFlat) {
        return customerGridFlatRepository.save(customerGridFlat);
    }

    @Override
    public CustomerAddressEntity findAddressByEntityId(final Integer addressId) {

        return customerAddressEntityRepository.findByEntityId(addressId);
    }

    @Override
    public CustomerAddressEntity saveAndFlushAddressEntity(final CustomerAddressEntity addressEntity) {
        return customerAddressEntityRepository.saveAndFlush(addressEntity);
    }

    @Override
    public String resetPassword(String email, Integer storeId, String magentoBaseUrl) throws CustomerException {
        return customRestTemplate.resetPassword(email, storeId, magentoBaseUrl);
    }

    @Override
    public Boolean exitsById(Integer customerId) {
    	
        return customerEntityRepository.existsById(customerId);
    }

    @Override
    public WishlistEntity findByCustomerId(Integer customerId) {
    	
    	WishlistEntity wishlistEntity = null;
    	if(StringUtils.isNotBlank(region) && region.equalsIgnoreCase("GCC")) {
    		return wishlistRepository.findById(customerId).orElse(null);
    	}else if(StringUtils.isNotBlank(region) && region.equalsIgnoreCase("IN")) {
    		
    		return wishlistInRepository.findById(customerId).orElse(null);
    	}
    	
    	return wishlistEntity;
    }

    @Override
    public Store findByStoreId(Integer storeId) {
        return storeRepository.findByStoreId(storeId);
    }



    @Override
    public WishlistEntity saveandFlushWishlistEntity(WishlistEntity entity) {
    	WishlistEntity wishlistEntity = null;
    	if(StringUtils.isNotBlank(region) && region.equalsIgnoreCase("GCC")) {
    		return wishlistRepository.save(entity);
    	}else if(StringUtils.isNotBlank(region) && region.equalsIgnoreCase("IN")) {
    		
    		return wishlistInRepository.save(entity);
    	}
    	
    	return wishlistEntity;
        
    }

    @Override
    public List<Integer> findByWebSiteId(Integer websiteId) {
        return storeRepository.findByWebSiteId(websiteId).stream().map(Store::getStoreId).collect(Collectors.toList());
    }

   

    @Override
    public List<Stores> getStoresArray() {
        return staticComponents.getStoresArray();
    }

    @Override
    public SearchClient getAlgoliaClient() {
        return staticComponents.getAlgoliaClient();
    }

    @Override
    public CustomerEntity findByPhoneNumber(String phoneNumber) {
        if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
            try {
                return customerEntityRepository.findByPhoneNumber(phoneNumber);
            } catch (IncorrectResultSizeDataAccessException ex) {
                LOGGER.info("[enableCustomerServiceErrorHandling] IncorrectResultSizeDataAccessException handled for phoneNumber: " + phoneNumber + ". Using mongoTemplate fallback.");
                Query query = new Query(Criteria.where("phoneNumber").is(phoneNumber));
                List<CustomerEntity> fallbackResults = mongoTemplate.find(query, CustomerEntity.class);
                return fallbackResults.isEmpty() ? null : fallbackResults.get(0);
            }
        } else {
            return customerEntityRepository.findByPhoneNumber(phoneNumber);
        }
   }

    @Override
    public CustomerEntity findByCardNumber(String cardNumber) {
        return customerEntityRepository.findByCardNumber(cardNumber);
    }

	@Override
	public CustomerEntity saveAndFlushMongoCustomerDocument(CustomerEntity customerEntity) {
		return customerEntityRepository.save(customerEntity);
	}

	@Override
	public List<WishlistItem> getWishlistItems(Integer wishlistId , Integer storeId) {
		WishlistEntity wishlistEntity = wishlistRepository.findById(wishlistId).orElse(null);
		
        if (wishlistEntity != null) {
        	List<WishlistItem> wishListItemList = 
        	wishlistEntity.getWishListItems().stream()
	            .filter(e -> storeId.equals(e.getStoreId()))
	            .sorted(Comparator.comparing(WishlistItem::getCreatedOn).reversed()) 
	            .collect(Collectors.toList());
        	wishListItemList =  wishlistEntity.getWishListItems();
        	
        	return wishListItemList;
        }
        return Collections.emptyList();
	}


}
