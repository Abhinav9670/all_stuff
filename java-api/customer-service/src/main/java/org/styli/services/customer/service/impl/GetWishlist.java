package org.styli.services.customer.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.model.Wishlist.WishlistEntity;
import org.styli.services.customer.model.Wishlist.WishlistItem;
import org.styli.services.customer.pojo.ProductStatusRequest;
import org.styli.services.customer.pojo.StoreDetailsResponse;
import org.styli.services.customer.pojo.Stores;
import org.styli.services.customer.pojo.elastic.ProductDetailsList;
import org.styli.services.customer.pojo.elastic.ResponseDetailElastic;
import org.styli.services.customer.pojo.elastic.VmDetailListResponse;
import org.styli.services.customer.pojo.elastic.request.ProductDetailListRequest;
import org.styli.services.customer.pojo.registration.request.CustomerWishlistV5Request;
import org.styli.services.customer.pojo.registration.response.CustomerWishListBody;
import org.styli.services.customer.pojo.registration.response.CustomerWishlistResponse;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;
import org.styli.services.customer.pojo.registration.response.Product.ProductValue;
import org.styli.services.customer.pojo.response.ProductInventoryRes;
import org.styli.services.customer.repository.Customer.WishlistRepository;
import org.styli.services.customer.service.Client;
import org.styli.services.customer.utility.Constants;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class GetWishlist {

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@Autowired
	private WishlistRepository wishlistRepository;

	private static final Log LOGGER = LogFactory.getLog(GetWishlist.class);

	private static final ObjectMapper mapper = new ObjectMapper();

	public CustomerWishlistResponse get(CustomerWishlistV5Request request, Client client, String vmUrl,
			RestTemplate restTemplate) {
		CustomerWishlistResponse resp = new CustomerWishlistResponse();
		CustomerWishListBody customerWishListBody = new CustomerWishListBody();
		List<ProductDetailsResponseV4DTO> products = new ArrayList<>();
		Integer minimumWishListListSize = null;
		List<WishlistItem> wishListItems = null;

		try {

			boolean customerExists = false;
			try {
				customerExists = client.exitsById(request.getCustomerId());
			} catch (Exception e2) {
				System.out.println(e2);
			}

			if (!customerExists) {
				resp.setStatus(false);
				resp.setStatusCode("202");
				resp.setStatusMsg("Customer not found!");
				return resp;
			}

			List<Stores> stores = client.getStoresArray();
			Stores store = stores.stream().filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
					.findAny().orElse(null);

			if (store == null || store.getStoreCode() == null || store.getStoreCurrency() == null) {
				resp.setStatus(false);
				resp.setStatusCode("202");
				resp.setStatusMsg("Store not found!");
				return resp;
			}

			Integer webSiteId = store.getWebsiteId();
			List<Integer> storeIds = stores.stream().filter(s -> s.getWebsiteId() == webSiteId)
					.map(e -> Integer.parseInt(e.getStoreId())).collect(Collectors.toList());
			StoreDetailsResponse storeDetailsResponse = new StoreDetailsResponse();
			storeDetailsResponse.setId(request.getStoreId());
			storeDetailsResponse.setCode(store.getStoreCode());
			storeDetailsResponse.setCurrency(store.getStoreCurrency());

			WishlistEntity wishList = client.findByCustomerId(request.getCustomerId());
			if (wishList != null) {
				customerWishListBody.setCustomerId(request.getCustomerId());
				 // wishListItems = client.getWishlistItems(wishList.getId(), request.getStoreId());
				wishListItems = wishList.getWishListItems().stream()
			            .filter(e -> storeIds.contains(e.getStoreId()))
			            .sorted(Comparator.comparing(WishlistItem::getCreatedOn).reversed()) 
			            .collect(Collectors.toList());
				if (CollectionUtils.isNotEmpty(wishListItems)
						&& null != ServiceConfigs.consulServiceMap.get("wishlist_minimum_count")) {

					minimumWishListListSize = (Integer) ServiceConfigs.consulServiceMap.get("wishlist_minimum_count");
					LOGGER.info("CustomerWishlist: " + "minimumWishListListSize:" + minimumWishListListSize);
					if (wishListItems.size() > minimumWishListListSize.intValue()) {
						wishListItems = wishListItems.stream().limit(minimumWishListListSize)
								.collect(Collectors.toList());
					}
					LOGGER.info("CustomerWishlist: " + "Calling Curofy ");
					products = curofyCall(request, vmUrl, restTemplate, resp, wishListItems, request.getCustomerId(), client, customerWishListBody,wishList);
				}
			}
			if (!products.isEmpty()) {
				LOGGER.info("CustomerWishlist: " + "Calling processProducts ");
				return processProducts(customerWishListBody, products, request);
			}
		} catch (Exception e) {
			LOGGER.info("CustomerWishlist: " + "Exception in  getwishlist " +e);
		}
		return resp;
	}

	private CustomerWishlistResponse processProducts(CustomerWishListBody customerWishListBody,
													 List<ProductDetailsResponseV4DTO> products,
													 CustomerWishlistV5Request request) {

		CustomerWishlistResponse resp = new CustomerWishlistResponse();
		Integer pageSize = request.getPageSize();
		Integer offset = request.getPageOffset();

		try {

			if (Boolean.TRUE.equals(request.getEnableQuantity())) {
				List<org.styli.services.customer.utility.pojo.config.Stores> stores = Constants.getStoresList();
				org.styli.services.customer.utility.pojo.config.Stores store =  stores.stream()
						.filter(e -> Integer.valueOf(e.getStoreId()).equals(request.getStoreId()))
						.findAny()
						.orElse(null);
				customerWishListBody.setDeepLink(store.getWebsiteIdentifier());
				AtomicInteger totalQuantity = new AtomicInteger();
				products.forEach(product -> {
					int totalQuantityAvailable = product.getConfigProducts().stream()
							.mapToInt(configProduct -> {
								String quantityStr = configProduct.getQuantity();
								return quantityStr != null && !quantityStr.isEmpty() ? Integer.parseInt(quantityStr) : 0;
							})
							.sum();
					product.setQuantityAvailable(totalQuantityAvailable);
					totalQuantity.addAndGet(totalQuantityAvailable);
				});
				if (totalQuantity.get()==0) {
					customerWishListBody.setAllOOS(true); // Set all out-of-stock flag
				} else {
					customerWishListBody.setAllOOS(false); // Optional: explicitly set it to false
				}
			}

			// Check the quantity of each SKU for each product and determine if it is out of stock
			List<ProductDetailsResponseV4DTO> outOfStockProducts = products.stream().filter(
							product -> product.getConfigProducts().stream().allMatch(sku -> sku.getQuantity() != null && sku.getQuantity().equals("0")))
					.collect(Collectors.toList());

			long outOfStockCount = outOfStockProducts.size();
			LOGGER.info("CustomerWishlist: " + "outOfStockProducts: " + outOfStockCount);
			LOGGER.info("CustomerWishlist: " + "outOfStockProducts: " + outOfStockProducts.toArray());

			// Sorting to put out-of-stock items at the bottom of the list
			List<ProductDetailsResponseV4DTO> sortedProducts = new ArrayList<>(products);
			Comparator<ProductDetailsResponseV4DTO> customComparator = (product1, product2) -> {
				boolean isOutOfStock1 = outOfStockProducts.contains(product1);
				boolean isOutOfStock2 = outOfStockProducts.contains(product2);

				// Compare based on out-of-stock status
				return (isOutOfStock1 && !isOutOfStock2) ? 1 : (!isOutOfStock1 && isOutOfStock2) ? -1 : 0;
			};
			Collections.sort(sortedProducts, customComparator);

			LOGGER.info("CustomerWishlist: " + "sorting done: " + products.toArray());

			if (Objects.nonNull(pageSize) && Objects.nonNull(offset)) {
				int startIndex = offset * pageSize;
				int endIndex = Math.min(startIndex + pageSize, sortedProducts.size());
				if (startIndex < endIndex) {
					List<ProductDetailsResponseV4DTO> displayedItems = sortedProducts.subList(startIndex, endIndex);
					customerWishListBody.setProducts(displayedItems);
					customerWishListBody.setProductCount(displayedItems.size());
					customerWishListBody.setTotalProductCount(sortedProducts.size());
					LOGGER.info("CustomerWishlist: " + " ProductCount: " + displayedItems.size() + " TotalProductCount:"
							+ sortedProducts.size());
				} else {
					// Set response for no content
					resp.setResponse(null);
					resp.setStatus(true);
					resp.setStatusCode("204");
					resp.setStatusMsg("No Content");
					return resp;
				}
			} else {
				// If pageSize or offset is not provided, set the entire list
				customerWishListBody.setProducts(sortedProducts);
				customerWishListBody.setProductCount(sortedProducts.size());
				customerWishListBody.setTotalProductCount(sortedProducts.size());
			}

			resp.setResponse(customerWishListBody);
			resp.setStatus(true);
			resp.setStatusCode("200");
			resp.setStatusMsg("Success!");
			LOGGER.info("CustomerWishlist: " + " returning from processProducts ");

		} catch (Exception e) {
			LOGGER.info("CustomerWishlist: " + "Exception in processProducts " + e);
		}
		return resp;
	}


	private List<ProductDetailsResponseV4DTO> curofyCall(CustomerWishlistV5Request request, String vmUrl,
			RestTemplate restTemplate, CustomerWishlistResponse resp, List<WishlistItem> wishListItems, Integer customerId, Client client, CustomerWishListBody customerWishListBody, WishlistEntity wishlistEntity) {

		try {
			LOGGER.info("CustomerWishlist: " + " Inside curofyCall ");
			Comparator<WishlistItem> reverseComparator = (c1, c2) -> c2.getCreatedOn().compareTo(c1.getCreatedOn());
			Collections.sort(wishListItems, reverseComparator);

			List<String> skusInWishlist = wishListItems.stream()
	                .map(WishlistItem::getSku)
	                .map(String::valueOf)
	                .collect(Collectors.toList());

			Map<String, Integer> productIdWishlistItemIdMap = new LinkedHashMap<>();
			int counter = 1;
			boolean  showNotificationValue = customerWishListBody.isShowPriceDropNotification();
			customerWishListBody.setShowPriceDropNotification(false);
			boolean priceChangeOnanyPrice = false;
			for (WishlistItem item : wishListItems) {
			    productIdWishlistItemIdMap.put(String.valueOf(item.getSku()), counter);
			    counter++;
			}
			if (CollectionUtils.isNotEmpty(skusInWishlist)) {
				try {
					ProductDetailListRequest elasticRequest = new ProductDetailListRequest();
					elasticRequest.setSkus(skusInWishlist);
					elasticRequest.setStoreId(request.getStoreId());
					elasticRequest.setCityId(request.getCityId());
					elasticRequest.setIsWishListCall(true);

					HttpHeaders requestHeaders = new HttpHeaders();
					requestHeaders.setContentType(MediaType.APPLICATION_JSON);
					requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
					requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
					HttpEntity<ResponseDetailElastic> requestBody = new HttpEntity(elasticRequest, requestHeaders);

					String detailsVmUrl = vmUrl + "/api/productDetail/list";
					LOGGER.info("Wishlist VM url: " + detailsVmUrl);
					LOGGER.info("request Body:" + mapper.writeValueAsString(requestBody.getBody()));
					ResponseEntity<VmDetailListResponse> response = restTemplate.exchange(detailsVmUrl, HttpMethod.POST,
							requestBody, VmDetailListResponse.class);

					VmDetailListResponse body = response.getBody();
					LOGGER.info("response Body:" + mapper.writeValueAsString(body));

					if (Objects.nonNull(body) && body.isStatus() && "200".equals(body.getStatusCode())) {
						ProductDetailsList productResponse = body.getResponse();
						AtomicReference<Boolean> isPriceMismatch= new AtomicReference<>(false);
						productResponse.getProductDetails()
								.forEach(productDetailsResponseV4DTO -> {
									final WishlistItem wishlistItem1= wishListItems.stream().filter(wishlistItem -> StringUtils.isNotEmpty(wishlistItem.getSku()) && StringUtils.isNotBlank(wishlistItem.getSku()) && wishlistItem.getSku().equals(productDetailsResponseV4DTO.getSku()))
											.findFirst()
											.orElse(null);
									if(wishlistItem1!=null){
										double previouslySavedPrice=0.0;
										double newCurrentPrice=Double.parseDouble(productDetailsResponseV4DTO.getPrices().getPrice());
										LOGGER.info("special price: "+ productDetailsResponseV4DTO.getPrices().getSpecialPrice());
										if(productDetailsResponseV4DTO.getPrices()!=null && productDetailsResponseV4DTO.getPrices().getSpecialPrice()!= null && !productDetailsResponseV4DTO.getPrices().getSpecialPrice().isEmpty() && !productDetailsResponseV4DTO.getPrices().getSpecialPrice().trim().isEmpty() && new BigDecimal(productDetailsResponseV4DTO.getPrices().getSpecialPrice()).compareTo(BigDecimal.ZERO)>0){
											newCurrentPrice= Double.parseDouble(productDetailsResponseV4DTO.getPrices().getSpecialPrice());
										}

										String currency= productDetailsResponseV4DTO.getCurrency();
										// SFP-305 WishlIst add new shipment mode in response
										if ("Local".equalsIgnoreCase(productDetailsResponseV4DTO.getShipmentMode())) {
											productDetailsResponseV4DTO.setShipmentMode("Express");
										}
										if( wishlistItem1.getCurrency()!= null  && productDetailsResponseV4DTO.getCurrency() != null && wishlistItem1.getCurrency().equals(productDetailsResponseV4DTO.getCurrency())){
											if (!String.valueOf(wishlistItem1.getLastPrice()).isEmpty() && wishlistItem1.getLastPrice() >0 ) {
												previouslySavedPrice= wishlistItem1.getLastPrice();
												if(wishlistItem1.getLastPrice() != newCurrentPrice) {
													isPriceMismatch.set(true);
												}
												if (wishlistItem1.getLastPrice() > newCurrentPrice || (wishlistItem1.getPreviouslySavedPrice() != null && wishlistItem1.getPreviouslySavedPrice() > wishlistItem1.getLastPrice())) {

														AtomicReference<Boolean> isOutOfStockProduct = new AtomicReference<>(true);
														for (ProductDetailsResponseV4DTO p : productDetailsResponseV4DTO.getConfigProducts()) {

															if (p.getQuantity() != null && !p.getQuantity().isEmpty() && Integer.parseInt(p.getQuantity()) > 0) {
																isOutOfStockProduct.set(false);
																break;
															}

														}
														LOGGER.info("isOutOfStockProduct: " + isOutOfStockProduct);
														if (!isOutOfStockProduct.get()) {

															if (request.getShowPriceDropNotification() != null && request.getShowPriceDropNotification()) {
																if (wishlistItem1.getPreviouslySavedPrice() != null && wishlistItem1.getPreviouslySavedPrice() > wishlistItem1.getLastPrice()) {
																	LOGGER.info(wishlistItem1.getPreviouslySavedPrice() - wishlistItem1.getLastPrice());
																	productDetailsResponseV4DTO.setWishlistPriceDrop(BigDecimal.valueOf(wishlistItem1.getPreviouslySavedPrice() - wishlistItem1.getLastPrice()).setScale(2, RoundingMode.HALF_UP).doubleValue());
																} else {
																	productDetailsResponseV4DTO.setWishlistPriceDrop(BigDecimal.valueOf(wishlistItem1.getLastPrice() - newCurrentPrice).setScale(2, RoundingMode.HALF_UP).doubleValue());
																}
																customerWishListBody.setShowPriceDropNotification(false);
															} else {
																if (wishlistItem1.getLastPrice() > newCurrentPrice) {
																	productDetailsResponseV4DTO.setWishlistPriceDrop(BigDecimal.valueOf(wishlistItem1.getLastPrice() - newCurrentPrice).setScale(2, RoundingMode.HALF_UP).doubleValue());
																	customerWishListBody.setShowPriceDropNotification(true);
																} else {
																	productDetailsResponseV4DTO.setWishlistPriceDrop(0.0);
																}
															}

														}
												} else {
														productDetailsResponseV4DTO.setWishlistPriceDrop(0);
												}

											}else{
												previouslySavedPrice= 0.0;
											}
										}

										LOGGER.info("CustomerWishlistCustomerId: " +customerId + " " +currency+ " " + newCurrentPrice + " "+ customerWishListBody.isShowPriceDropNotification());
										// WishlistEntity wishlistEntity = client.findByCustomerId(customerId);
                                        double finalPreviouslySavedPrice = previouslySavedPrice;
                                            LOGGER.info("price: "+finalPreviouslySavedPrice+" "+newCurrentPrice);
                                            final double currentPrice= newCurrentPrice;
											wishlistEntity.getWishListItems().stream().filter(item ->
													StringUtils.isNotEmpty(item.getSku()) && StringUtils.isNotBlank(item.getSku()) && item.getSku().equals(productDetailsResponseV4DTO.getSku())).findFirst()
													.ifPresent(item -> {item.setLastPrice(currentPrice); item.setCurrency(currency); item.setPreviouslySavedPrice(finalPreviouslySavedPrice);});
                                                                               
                                    }
								});
						
						if(showNotificationValue || customerWishListBody.isShowPriceDropNotification() || isPriceMismatch.get()) {
							
						 wishlistRepository.save(wishlistEntity);
						 
						}
						
						// Sort the product details based on the SKU-WishlistItemId map
						List<ProductDetailsResponseV4DTO> sortedProductDetails = productResponse.getProductDetails().stream()
						        .sorted(Comparator.comparingInt(dto -> productIdWishlistItemIdMap.get(String.valueOf(dto.getSku()))))
						        .collect(Collectors.toList());
						
						LOGGER.info("value set: done");

						// Set the sorted product details back to the response
						productResponse.setProductDetails(sortedProductDetails);

						return sortedProductDetails;
	                }
				} catch (Exception e) {
					LOGGER.error("Error in calling VM APM : ", e);
					resp.setStatus(false);
					resp.setStatusCode("207");
					resp.setStatusMsg("Product could not be fetched!");
				}
			}

		} catch (Exception e) {
			LOGGER.info("CustomerWishlist: " + "Exception in  curofycall " +e);
		}
		return new ArrayList<>();
	}



	public List<ProductValue> getInventoryQty(ProductStatusRequest productStatusRequest) {
		List<ProductValue> response = new ArrayList<>();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
		requestHeaders.add(Constants.AUTH_BEARER_HEADER, getAuthorization(internalHeaderBearerToken));
		HttpEntity<ProductStatusRequest> requestBody = new HttpEntity<>(productStatusRequest, requestHeaders);
		String url = Constants.getInventoryBaseUrl() + "/api/inventory/storefront/atp";
		try {
			LOGGER.info("Inventory Request : " + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<ProductInventoryRes> responseBody = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					ProductInventoryRes.class);

			ProductInventoryRes body = new ProductInventoryRes();
			if (responseBody.getStatusCode() == HttpStatus.OK) {
				body = responseBody.getBody();
				if (body != null) {
					return body.getResponse();
				}
			} else {
				LOGGER.error("Error  from InventoryFetch:" + body.getStatusMsg());
			}
		} catch (Exception e) {
			LOGGER.error("Exception occurred:" + e.getMessage());
		}
		return response;
	}

	public String getAuthorization(String authToken) {
		String token = null;
		if (StringUtils.isNotEmpty(authToken)) {
			if (authToken.contains(",")) {
				List<String> authTokenList = Arrays.asList(authToken.split(","));
				if (CollectionUtils.isNotEmpty(authTokenList)) {
					token = authTokenList.get(0);
				}
			} else {
				return authToken;
			}
		}
		return token;
	}
}
