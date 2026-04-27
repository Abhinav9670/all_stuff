package org.styli.services.customer.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.pojo.ImageDetails;
import org.styli.services.customer.pojo.PriceDetails;
import org.styli.services.customer.pojo.StoreDetailsResponse;
import org.styli.services.customer.pojo.elastic.HitsDetailResponseElastic;
import org.styli.services.customer.pojo.elastic.ResponseDetailElastic;
import org.styli.services.customer.pojo.elastic.request.NumericFilter;
import org.styli.services.customer.pojo.elastic.request.ProductListRequestV2;
import org.styli.services.customer.pojo.registration.request.CustomerWishListRequest;
import org.styli.services.customer.pojo.registration.request.GetProductV4Request;
import org.styli.services.customer.pojo.registration.request.WishProduct;
import org.styli.services.customer.pojo.registration.response.ProductsHitsResponseV4;
import org.styli.services.customer.pojo.registration.response.Product.ProductDetailsResponseV4DTO;
import org.styli.services.customer.utility.Constants;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Kushal, 20/09/2020
 * @project micro-services-customer
 */

@Component
public class WishlistHelperV5 {

	private static final Log LOGGER = LogFactory.getLog(WishlistHelperV5.class);

  

    private final Map<String,String> productWishlistItemIdMap = new HashMap<String,String>();

    private static final ObjectMapper mapper = new ObjectMapper();

    public ProductDetailsResponseV4DTO convertResponseFromAlgolia(ProductsHitsResponseV4 result,
            StoreDetailsResponse storeDetailsResponse, String indexName,
            Integer wishlistItemId) {

        ProductDetailsResponseV4DTO response = new ProductDetailsResponseV4DTO();
        response.setSource(indexName);

        response.setWishListItemId("" + wishlistItemId);
        response.setId(parseNullStr(result.getObjectID()));
        response.setBrand(parseNullStr(result.getBrandName()));
        response.setName(parseNullStr(result.getName()));
        if (result.getSku() != null && CollectionUtils.isNotEmpty(result.getSku())) {
            response.setSku(result.getSku().get(0));
        }
        response.setProductType(parseNullStr(result.getTypeId()));

        ImageDetails imageDetails = new ImageDetails();
        imageDetails.setImage(parseNullStr(result.getImageUrl()));
        imageDetails.setThumbnail(parseNullStr(result.getThumbnailUrl()));
        response.setImages(imageDetails);

        response.setCurrency(parseNullStr(storeDetailsResponse.getCurrency()));
        if (result.getVisibilityCatalog() != null && result.getVisibilityCatalog() == 1) {
            response.setIsVisibleProduct(true);
        } else {
            response.setIsVisibleProduct(false);
        }
        response.setUrl(parseNullStr(result.getUrl()));

        PriceDetails prices = new PriceDetails();
        response.setPrices(prices);

        return response;
    }

    private String parseNullStr(Object val) {
        return (val == null) ? null : String.valueOf(val);
    }

    public List<ProductDetailsResponseV4DTO> retrieveProductDetailsFrmCurofy(String env, String vmUrl, ElasticProductHelperV5 elasticProductHelperV5,
            RestTemplate restTemplate, CustomerWishListRequest customerWishListReq) {

    	List<ProductDetailsResponseV4DTO> products = new ArrayList<>();
    	try {

            ProductListRequestV2 elasticRequest = new ProductListRequestV2();
            HashMap<String, List<String>> filters = new HashMap<>();
            List<WishProduct> wishlistProducts = customerWishListReq.getWishList();

            List<String> productIdsInWishlist = wishlistProducts.stream().map(e -> String.valueOf(e.getParentProductId())).collect(Collectors.toList());
            filters.put("sku", productIdsInWishlist);
            elasticRequest.setFilters(filters);
            elasticRequest.setNumericFilters(new ArrayList<NumericFilter>());
            elasticRequest.setPageOffset(0);
            elasticRequest.setPageSize(productIdsInWishlist.size());
            elasticRequest.setStoreId(customerWishListReq.getStoreId());
            elasticRequest.setCategoryLevel(3);
            if (env.equals("live")) {
                elasticRequest.setEnv(Constants.LISTING_VM_ENV_LIVE);
            } else {
                elasticRequest.setEnv(env);
            }

            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setContentType(MediaType.APPLICATION_JSON);
            requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);
            HttpEntity<ResponseDetailElastic> requestBody = new HttpEntity(elasticRequest, requestHeaders);

            String detailsVmUrl = vmUrl + "/api/detail";
            LOGGER.info("Wishlist VM url: " + detailsVmUrl);
            LOGGER.info("request Body:" + mapper.writeValueAsString(requestBody.getBody()));
            ResponseEntity<ResponseDetailElastic> response = restTemplate.exchange(detailsVmUrl,
                    HttpMethod.POST, requestBody, ResponseDetailElastic.class);
            ResponseDetailElastic body = response.getBody();

            LOGGER.info("request Body:" + mapper.writeValueAsString(response.getBody()));

            if (response.getStatusCode() == HttpStatus.OK && body != null
                    && CollectionUtils.isNotEmpty(body.getHits())) {
                List<ProductDetailsResponseV4DTO> nativeProductsDetails = new ArrayList<>();

                for (HitsDetailResponseElastic hit : body.getHits()) {
                    if (hit != null) {
                        ProductDetailsResponseV4DTO responseV4DTO = elasticProductHelperV5
                                .getDetailedProduct(body, hit, customerWishListReq.getStoreId(),
                                		productWishlistItemIdMap);
                        if (responseV4DTO != null) {
                            nativeProductsDetails.add(responseV4DTO);
                        }
                    }
                }

                products.addAll(nativeProductsDetails);

            } else {
            	LOGGER.error("Response not found while retrieving details from curofy");
            }
        } catch (Exception e) {
            LOGGER.error("Error occurred while retrieving details from curofy" + e.getMessage());
        }

    	return products;
    }
}
