package org.styli.services.customer.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.component.ConsulComponent;
import org.styli.services.customer.helper.ElasticProductHelperV5;
import org.styli.services.customer.helper.WishlistHelperV5;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.model.Wishlist.WishlistItem;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.registration.request.CustomerWishListRequest;
import org.styli.services.customer.pojo.registration.request.WishProduct;
import org.styli.services.customer.pojo.registration.response.CustomerWishListBody;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;
import org.styli.services.customer.service.Client;
import org.apache.commons.collections.CollectionUtils;

@Component
public class AddWishlist {
	
	private static final Log LOGGER = LogFactory.getLog(ConsulComponent.class);

	@Autowired
	WishlistHelperV5 wishlistHelper;
	
	@Value("${env}")
    String env;
	

    public CustomerWishlistResponse add(CustomerWishListRequest customerWishListReq, boolean isSave, Client client,
            Map<String, String> requestHeader, ElasticProductHelperV5 elasticProductHelperV5, RestTemplate restTemplate, String vmUrl) {

        CustomerWishlistResponse response = new CustomerWishlistResponse();

		if (null == customerWishListReq.getCustomerId()) {

			response.setStatus(false);
			response.setStatusCode("209");
			response.setStatusMsg("Invalid Customer Id");
			
			return response;
		}
        WishlistEntity wishListEntity = null;

        try {

            boolean isExistCustomer = client.exitsById(customerWishListReq.getCustomerId());

            if (isExistCustomer) {

                wishListEntity = client.findByCustomerId(customerWishListReq.getCustomerId());
                wishListEntity = isWishlistEmpty(customerWishListReq, wishListEntity);

                List<Stores> stores = client.getStoresArray();
                Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(customerWishListReq.getStoreId()))
                        .findAny().orElse(null);
                
                if (null == store) {

                    response.setStatus(false);
                    response.setStatusCode("201");
                    response.setStatusMsg("Invalid Store");

                    return response;
                }

                List<Integer> storeIds = stores.stream().filter(s -> s.getWebsiteId()== store.getWebsiteId()).map(e -> Integer.parseInt(e.getStoreId()))
                        .collect(Collectors.toList());

                for (WishProduct product : customerWishListReq.getWishList()) {

                	WishlistItem wishlistItemExists = null;

                	wishlistItemExists = checkIfWishlistItemExistsAlready(wishListEntity, storeIds, product);

                    if (null != wishlistItemExists) {
                    	
                    	try {
							List<WishlistItem> wishListItems = wishListEntity.getWishListItems();
							for (WishlistItem item : wishListItems) {
							    if (item.getSku().equals(wishlistItemExists.getSku())) { 
							        item.setCreatedOn(new Date());
							        break; 
							    }
							}
							client.saveandFlushWishlistEntity(wishListEntity);
						} catch (Exception e) {
							LOGGER.info("error while updating wishlist object craeted date to db", e);
						}

                        CustomerWishListBody responseBody = new CustomerWishListBody();

                        responseBody.setWishListId(null);
                        responseBody.setCustomerId(customerWishListReq.getCustomerId());
                        responseBody.setWishListItemId(getWishlistItemId(product));
                        response.setResponse(responseBody);
                        response.setStatus(true);
                        response.setStatusCode("200");
                        response.setStatusMsg("Updated succesfully!!");

                        return response;

                    } else {

                    	String sku = createNewWishlistItem(customerWishListReq, requestHeader, elasticProductHelperV5, restTemplate,
								vmUrl, wishListEntity, product);

                        client.saveandFlushWishlistEntity(wishListEntity);
                        
                        CustomerWishListBody responseBody = new CustomerWishListBody();
                        responseBody.setWishListId(null);
                        responseBody.setCustomerId(customerWishListReq.getCustomerId());
                        if(StringUtils.isEmpty(sku)) {
                        	responseBody.setWishListItemId(product.getSku());
                        } else {
                        	responseBody.setWishListItemId(sku);
                        }
                        response.setResponse(responseBody);
                        response.setStatus(true);
                        response.setStatusCode("200");

                        }
                }

                logStatusMsgInResponse(isSave, response);

            } else {

                return errorWishListRes(response, "204", "Invalid CustomerId");
            }

        } catch (DataAccessException exception) {

            response = errorWishListRes(response, "204", "ERROR !!");

            response.setError(getError("400", exception.getMessage()));
        }

        return response;
    
    }

	private WishlistEntity isWishlistEmpty(CustomerWishListRequest customerWishListReq, WishlistEntity wishListEntity) {
		if (null == wishListEntity) {

		    wishListEntity = new WishlistEntity();
		    wishListEntity.setId(customerWishListReq.getCustomerId());

		}
		return wishListEntity;
	}

	private void logStatusMsgInResponse(boolean isSave, CustomerWishlistResponse response) {
		if (isSave) {

		    response.setStatusMsg("Saved SuccessFully!!");

		} else {

		    response.setStatusMsg("Updated SuccessFully!!");
		}
	}

	private String getWishlistItemId(WishProduct product) {
		return product.getWishListItemId() != null ? product.getWishListItemId() : product.getSku();
	}

	private String createNewWishlistItem(CustomerWishListRequest customerWishListReq, Map<String, String> requestHeader,
			ElasticProductHelperV5 elasticProductHelperV5, RestTemplate restTemplate, String vmUrl,
			WishlistEntity wishListEntity, WishProduct product) {
		String sku = null;
		List<WishlistItem> wishlistItemList = wishListEntity.getWishListItems() != null ? wishListEntity.getWishListItems() : new ArrayList<>();
		WishlistItem wishlistItem = new WishlistItem();

		if(StringUtils.isEmpty(product.getSku())) {

			if(product.getParentProductId() != null) {

				sku = getFromCurofyProduct(customerWishListReq, elasticProductHelperV5, restTemplate, vmUrl, product,
						wishlistItem);

			}
		} else {

			wishlistItem.setPrice(product.getPrice());
			wishlistItem.setSpecialPrice(product.getSpecialPrice());
			double lastPrice= 0.0;
			if(StringUtils.isNotBlank(product.getPrice()) && StringUtils.isNotEmpty(product.getPrice()) && Double.parseDouble(product.getPrice())>0){
				lastPrice = Double.parseDouble(product.getPrice());
			}
			if(StringUtils.isNotBlank(product.getSpecialPrice()) && StringUtils.isNotEmpty(product.getSpecialPrice()) && Double.parseDouble(product.getSpecialPrice())>0){
				lastPrice= Double.parseDouble(product.getSpecialPrice());
			}
			wishlistItem.setLastPrice(lastPrice);
			wishlistItem.setPreviouslySavedPrice(lastPrice);
			wishlistItem.setSku(product.getSku());
			wishlistItem.setWishlistItemId(product.getSku());
		}

		 wishlistItem.setStoreId(customerWishListReq.getStoreId());
		 wishlistItem.setCreatedOn(new Date());
		 wishlistItem.setSource(product.getSource() != null ? product.getSource() : requestHeader.get("x-source") );
		 wishlistItem.setUtmCampaign(product.getUtmCampaign());
		wishlistItemList.add(wishlistItem);
		wishListEntity.setWishListItems(wishlistItemList);
		return sku;
	}

	private String getFromCurofyProduct(CustomerWishListRequest customerWishListReq,
			ElasticProductHelperV5 elasticProductHelperV5, RestTemplate restTemplate, String vmUrl, WishProduct product,
			WishlistItem wishlistItem) {
		ProductDetailsResponseV4DTO curofyProduct;
		String sku = null;
		// START - Curofy call
		List<ProductDetailsResponseV4DTO> products = wishlistHelper.retrieveProductDetailsFrmCurofy(env, vmUrl,
				elasticProductHelperV5, restTemplate, customerWishListReq);
		// END - Curofy Call

      if(products != null) {

		   curofyProduct = products.stream().filter(e -> e.getSku().equals(product.getParentProductId()))
		            .findAny().orElse(null);
		   
		   if(curofyProduct != null) {
			   
			   wishlistItem.setPrice(curofyProduct.getPrices().getPrice());
		       wishlistItem.setSpecialPrice(curofyProduct.getPrices().getSpecialPrice());
		       LOGGER.info("special price:"+curofyProduct.getPrices().getSpecialPrice());
			   double lastPrice=0.0;
			   if(curofyProduct.getPrices() != null && StringUtils.isNotEmpty(curofyProduct.getPrices().getPrice()) && StringUtils.isNotBlank(curofyProduct.getPrices().getPrice()) && Double.parseDouble(curofyProduct.getPrices().getPrice())>0){
				   lastPrice= Double.parseDouble(curofyProduct.getPrices().getPrice());
			   }
			   if(curofyProduct.getPrices() != null && StringUtils.isNotEmpty(curofyProduct.getPrices().getSpecialPrice()) &&  StringUtils.isNotBlank(curofyProduct.getPrices().getSpecialPrice()) && Double.parseDouble(curofyProduct.getPrices().getSpecialPrice())>0){
				   lastPrice= Double.parseDouble(curofyProduct.getPrices().getSpecialPrice());
			   }
			   wishlistItem.setLastPrice(lastPrice);
			   wishlistItem.setPreviouslySavedPrice(lastPrice);
		       wishlistItem.setCurrency(curofyProduct.getCurrency());
		       if(curofyProduct.getSku() != null) {
		    	   wishlistItem.setSku(curofyProduct.getSku());
		    	   wishlistItem.setWishlistItemId(curofyProduct.getSku());
		    	   sku = curofyProduct.getSku();
		       }
		   }
      }
      return sku;
	}

	private WishlistItem checkIfWishlistItemExistsAlready(WishlistEntity wishListEntity, List<Integer> storeIds,
			WishProduct product) {
		
		WishlistItem wishlistItemExists = null;
		if(wishListEntity.getWishListItems() != null && CollectionUtils.isNotEmpty(storeIds)) {

			if(StringUtils.isEmpty(product.getSku())) {

		    	if(null != product.getParentProductId()) {

		    		wishlistItemExists = wishListEntity.getWishListItems().stream()
		                .filter(e -> null != e.getSku() && e.getSku().equalsIgnoreCase(product.getParentProductId())
		                		&& storeIds.contains(e.getStoreId())).findAny()
		                .orElse(null);

			    }
			}else {

			    	wishlistItemExists = wishListEntity.getWishListItems().stream()
		                    .filter(e -> null != e.getSku() && e.getSku().equalsIgnoreCase(product.getSku())
		                    		&& storeIds.contains(e.getStoreId())).findAny()
		                    .orElse(null);
			    }
		}
		return wishlistItemExists;
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
