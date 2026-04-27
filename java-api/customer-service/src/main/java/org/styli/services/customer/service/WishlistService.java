package org.styli.services.customer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.utility.Constants;

import java.util.Date;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WishlistService {

    private final MongoTemplate mongoTemplate;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PubSubApplication.class);

    @Autowired
    public WishlistService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<WishlistEntity> findWishlistEntitiesByOptionIds(String optionId, Integer limit, Long skip) {
    	 List<WishlistEntity> entities = null;
    	try {
    	int pubsubWishlistNoOfDays=15;
        LOGGER.info("findWishlistEntitiesByOptionIds");
        if(Constants.getConsulConfigResponse().getPubsubWishlistNoOfDays()> 0){
            pubsubWishlistNoOfDays=Constants.getConsulConfigResponse().getPubsubWishlistNoOfDays();
        }
        LOGGER.info("pubsubWishlistNoOfDays");
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusDays(pubsubWishlistNoOfDays);
        Query query = new Query(Criteria.where("wishlist_items")
                .elemMatch(Criteria.where("createdOn").gte(Date.from(oneMonthAgo.toInstant(ZoneOffset.UTC)))
                        .and("sku").is(optionId))).limit(limit).skip(skip);
        LOGGER.info("query done");
        entities = mongoTemplate.find(query, WishlistEntity.class);


        entities.forEach(entity -> {
            entity.setWishListItems(entity.getWishListItems().stream()
                    .filter(item -> optionId.equalsIgnoreCase(item.getSku()))
                    .collect(Collectors.toList()));
        });
        LOGGER.info("for each done");
        
    	}catch(Exception ex) {
    		
    		 LOGGER.info("exception during fetch"+ex.getMessage());	
    	}

        return entities;

    }

    public Long countWishlistEntitiesByOptionIds(String optionId) {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        Query query = new Query(Criteria.where("wishlist_items")
                .elemMatch(Criteria.where("createdOn").gte(Date.from(oneMonthAgo.toInstant(ZoneOffset.UTC)))
                        .and("sku").is(optionId)));

        Long count = mongoTemplate.count(query, WishlistEntity.class);

        return count;

    }
}
