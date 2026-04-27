package org.styli.services.customer.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.helper.ElasticProductHelperV5;
import org.styli.services.customer.model.Store;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.model.Wishlist.WishlistItem;
import org.styli.services.customer.pojo.elastic.HitsDetailResponseElastic;
import org.styli.services.customer.pojo.elastic.ResponseDetailElastic;
import org.styli.services.customer.pojo.elastic.request.NumericFilter;
import org.styli.services.customer.pojo.elastic.request.ProductListRequestV2;
import org.styli.services.customer.pojo.registration.response.CustomerWishListBody;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.pojo.registration.response.WishValue;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.GenericConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GetWishlistIds {
	
	private static final Log LOGGER = LogFactory.getLog(GetWishlistIds.class);
	
	private static final ObjectMapper mapper = new ObjectMapper();

	private static final String SUCCESS = "SUCCESS !!";

    public CustomerWishlistResponse get(Integer customerId, Integer storeId, boolean standalone, Client client,
    		String env, ElasticProductHelperV5 elasticProductHelperV5, RestTemplate restTemplate, String vmUrl) {

        CustomerWishlistResponse response = new CustomerWishlistResponse();

        try {

            boolean isExistCustomer = client.exitsById(customerId);

            if (isExistCustomer) {

                List<ProductDetailsResponseV4DTO> productWishList = new ArrayList<>();
                List<WishValue> wishProductList = new ArrayList<>();
                List<WishlistItem> wishListItemEntityList = null;

                WishlistEntity wishListEntity = client.findByCustomerId(customerId);

                if (null != wishListEntity) {

                    Store store = client.findByStoreId(storeId);

                    wishListItemEntityList = checkIfValidStore(client, response, wishListItemEntityList, wishListEntity,
							store);

                    if (CollectionUtils.isNotEmpty(wishListItemEntityList)) {

                        CustomerWishListBody responseBody = new CustomerWishListBody();

	                    for (WishlistItem wishListItem : wishListItemEntityList) {
	
	                        WishValue value = new WishValue();
	
	                        value.setProductId(wishListItem.getSku());
	                        value.setWishListItemId(wishListItem.getWishlistItemId());
	
	                        wishProductList.add(value);
	
	                       }

                        responseBody.setProductIds(wishProductList);

                        responseBody.setCustomerId(customerId);
                        response.setResponse(responseBody);
                        response.setStatus(true);
                        response.setStatusCode("200");
                        response.setStatusMsg(SUCCESS);

                    }

                } else {

                    CustomerWishListBody responseBody = new CustomerWishListBody();

                    responseBody.setMessage("WishList Is Not Created");
                    response.setResponse(responseBody);
                    response.setStatus(true);
                    response.setStatusCode("201");
                    response.setStatusMsg(SUCCESS);
                }

            } else {

                response = errorWishListRes(response, "204", "Invalid Customer ID");
            }

        } catch (DataAccessException exception) {

            response = errorWishListRes(response, "204", "ERROR !!");

            response.setError(getError("400", exception.getMessage()));
        }

        return response;

    }

	private List<WishlistItem> checkIfValidStore(Client client, CustomerWishlistResponse response,
			List<WishlistItem> wishListItemEntityList, WishlistEntity wishListEntity, Store store) {
		Integer webSiteId = 0;
		if (null != store) {

		    webSiteId = store.getWebSiteId();

		    List<Integer> storeIds = client.findByWebSiteId(webSiteId);

		    wishListItemEntityList = new ArrayList<>(wishListEntity.getWishListItems())
		    		.stream().filter(e -> storeIds.contains(e.getStoreId())).collect(Collectors.toList());

		} else {

		    response.setStatus(false);
		    response.setStatusCode("201");
		    response.setStatusMsg("Invalid Store ID!");

		}
		return wishListItemEntityList;
	}

    /**
     * @param response
     * @param errorCode
     * @param errorMessage
     * @return
     */
    private CustomerWishlistResponse errorWishListRes(CustomerWishlistResponse response, String errorCode,
            String errorMessage) {

        response.setStatus(false);
        response.setStatusCode(errorCode);
        response.setStatusMsg(errorMessage);

        return response;
    }

    private ErrorType getError(String errorCode, String errorMessage) {

        ErrorType errorType = new ErrorType();

        errorType.setErrorCode(errorCode);
        errorType.setErrorMessage(errorMessage);

        return errorType;
    }

}
