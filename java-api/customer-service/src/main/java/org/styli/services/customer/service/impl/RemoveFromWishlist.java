package org.styli.services.customer.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.model.Wishlist.WishlistItem;
import org.styli.services.customer.pojo.registration.request.CustomerWishListRequest;
import org.styli.services.customer.pojo.registration.request.WishProduct;
import org.styli.services.customer.pojo.registration.response.CustomerWishListBody;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.pojo.registration.response.ErrorType;
import org.styli.services.customer.service.Client;

public class RemoveFromWishlist {

    private static final Log LOGGER = LogFactory.getLog(CustomerV4ServiceImpl.class);

    public CustomerWishlistResponse remove(CustomerWishListRequest customerWishListReq, Client client) {

        CustomerWishlistResponse response = new CustomerWishlistResponse();

        StringBuilder sb = new StringBuilder();

        try {

            boolean isExistCustomer = client.exitsById(customerWishListReq.getCustomerId());

            if (isExistCustomer) {

                WishlistEntity wishListEntity = client.findByCustomerId(customerWishListReq.getCustomerId());

                iterateOnWishList(customerWishListReq, client, sb, wishListEntity);

                CustomerWishListBody responseBody = new CustomerWishListBody();

                responseBody.setMessage(StringUtils.chop(sb.toString()) + " Deleted SuccessFully");
                responseBody.setCustomerId(customerWishListReq.getCustomerId());
                response.setResponse(responseBody);
                response.setStatus(true);
                response.setStatusCode("200");
                response.setStatusMsg("SUCCESS");

            } else {

                return errorWishListRes(response, "204", "Invalid Customer ID");

            }

        } catch (DataAccessException exception) {

            response = errorWishListRes(response, "204", "ERROR !!");

            response.setError(getError("400", exception.getMessage()));
        }

        return response;
    }

	private void iterateOnWishList(CustomerWishListRequest customerWishListReq, Client client, StringBuilder sb,
			WishlistEntity wishListEntity) {
		try {
		    if (null != wishListEntity) {
		    	
		    	List<WishlistItem> updatedWishlistItemList = wishListEntity.getWishListItems();

		        updatedWishlistItemList = iterateOnWishlistRequest(customerWishListReq, sb, wishListEntity,
						updatedWishlistItemList);
		        wishListEntity.setWishListItems(updatedWishlistItemList);
		        client.saveandFlushWishlistEntity(wishListEntity);
		    }
		} catch (DataAccessException e) {
		    LOGGER.error("error in deleting wishlist item");
		    LOGGER.error(e.getMessage());
		}
	}

	private List<WishlistItem> iterateOnWishlistRequest(CustomerWishListRequest customerWishListReq, StringBuilder sb,
			WishlistEntity wishListEntity, List<WishlistItem> updatedWishlistItemList) {
		for (WishProduct wishProduct : customerWishListReq.getWishList()) {
			if (null != wishProduct) {
				if (wishListEntity.getWishListItems() != null) {
					if (wishProduct.getWishListItemId() != null) {
						updatedWishlistItemList = wishListEntity.getWishListItems().stream()
								.filter(e -> null != e.getWishlistItemId()
										&& !e.getWishlistItemId().equalsIgnoreCase(wishProduct.getWishListItemId()))
								.collect(Collectors.toList());
					} else {
						updatedWishlistItemList = wishListEntity.getWishListItems().stream()
								.filter(e -> null != e.getSku() && !e.getSku().equalsIgnoreCase(wishProduct.getSku()))
								.collect(Collectors.toList());
					}
				}

				sb.append(wishProduct.getWishListItemId()).append(",");
			}
		}
		return updatedWishlistItemList;
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
