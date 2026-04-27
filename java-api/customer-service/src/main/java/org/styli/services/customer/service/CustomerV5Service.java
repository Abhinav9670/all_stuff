package org.styli.services.customer.service;

import java.util.Map;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;
import org.styli.services.customer.pojo.*;
import org.styli.services.customer.pojo.registration.request.AccessTokenResponse;
import org.styli.services.customer.pojo.registration.request.CustomerWishlistV5Request;
import org.styli.services.customer.pojo.registration.request.GetProductV4Request;
import org.styli.services.customer.pojo.registration.request.LogoutResponse;
import org.styli.services.customer.pojo.registration.response.AccessTokenRequest;
import org.styli.services.customer.pojo.registration.response.CustomerUpdateProfileResponse;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4;

/**
 * @author Umesh, 07/07/2020
 * @project product-service
 */

@Service
public interface CustomerV5Service {
	
	static final String CITY_CACHE_NAME = "city-bucket"; 
	
    CustomerWishlistResponse getWishList(CustomerWishlistV5Request request);

	CustomerOmsResponsedto customerOmslist(@Valid CustomerOmslistrequest request);

	CustomerOMSDeleteLoginHistoryResponse customerOmsDeleteLogin(@Valid CustomerOMSDeleteLoginHistoryRequest request);

	CustomerUpdateProfileResponse customerDetails(@Valid CustomerDetailsRequest request,@RequestHeader Map<String, String> httpRequestHeadrs);
	
	 ProductStatusResponse getProductQty(Map<String, String> requestHeader, ProductStatusRequest productStatusReq);
	
	ProductDetailsResponseV4 getProductInfo(@Valid GetProductV4Request request, String xHeaderToken);

	GetLocationGoogleMapsResponse getLocationGoogleMaps(@Valid GetLocationGoogleMapsRequest request);

	PlacesAutocompleteGoogleMapsResponse getGooglePlacesForAutocompleteText(String placeText, Integer storeId);

	ResponseEntity<?> refreshAccessToken(AccessTokenRequest tokenRequest, Map<String, String> requestHeader);
	
	LogoutResponse logout(String deviceId,Integer customerId);

}