package org.styli.services.order.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderItem;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrderItem;
import org.styli.services.order.pojo.mulin.GetProductsBySkuRequest;
import org.styli.services.order.pojo.mulin.GetProductsBySkuResponse;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.ratings.CustomerRatings;
import org.styli.services.order.pojo.ratings.DeleteCustomerRatingsReq;
import org.styli.services.order.pojo.ratings.RatingsResponse;
import org.styli.services.order.pojo.ratings.RetrieveRatingsRequest;
import org.styli.services.order.pojo.ratings.RetrieveRatingsResponse;
import org.styli.services.order.pojo.ratings.UpdateCustomerRatingsReq;
import org.styli.services.order.utility.Constants;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Component
public class MulinHelper {

    private static final Log LOGGER = LogFactory.getLog(MulinHelper.class);
    
    private static final String MULIN_RATIN_UPDATE = "/v1/ratings/update";
    
    private static final String USER_AGENT = "user-agent";
    
    private static final ObjectMapper mapper = new ObjectMapper();

    @Value("${mulin.base.url}")
    private String mulinBaseUrl;


    public Map<String, ProductResponseBody> getMulinProductsFromOrder(List<SalesOrder> orders, RestTemplate restTemplate) {
        Map<String, ProductResponseBody> productsFromMulin;
        Map<String, ProductResponseBody> productsByIdFromMulin = new HashMap<>();

        List<String> skus = new ArrayList<>();
        for (SalesOrder order : orders) {
            for (SalesOrderItem salesOrderItem : order.getSalesOrderItem()) {
                if (salesOrderItem.getParentOrderItem() == null) {
                    skus.add(salesOrderItem.getSku());
                }
            }
        }

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

        GetProductsBySkuRequest getProductsBySkuRequest = new GetProductsBySkuRequest();
        getProductsBySkuRequest.setSkus(skus);
        getProductsBySkuRequest.setSearchVariants(true);

        HttpEntity<GetProductsBySkuRequest> requestBody = new HttpEntity<>(getProductsBySkuRequest, requestHeaders);
        String url = mulinBaseUrl + "/v1/products/productsBySku";

        try {
        	 LOGGER.info("mulin requst URL:" + url);
             LOGGER.info("mulin request body:" + mapper.writeValueAsString(requestBody.getBody()));
            ResponseEntity<GetProductsBySkuResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
                    GetProductsBySkuResponse.class);
            
			GetProductsBySkuResponse body = response.getBody();
			LOGGER.info("mulin response body:" + mapper.writeValueAsString(body));
			if (response.getStatusCode() == HttpStatus.OK && body != null) {
				productsFromMulin = body.getResponse();

				for (Map.Entry<String, ProductResponseBody> entry : productsFromMulin.entrySet()) {
					productsByIdFromMulin.put(entry.getKey(), entry.getValue());
				}
			}
        } catch (RestClientException e) {
            LOGGER.error("exception occurred during mulin call:" + e.getMessage());
        }catch (Exception ex) {
            LOGGER.error("exception occurred during mulin call:" + ex.getMessage());
        }

        return productsByIdFromMulin;
    }

	public Map<String, ProductResponseBody> getMulinProductsFromSplitOrder(List<SplitSalesOrder> orders, RestTemplate restTemplate) {
		Map<String, ProductResponseBody> productsFromMulin;
		Map<String, ProductResponseBody> productsByIdFromMulin = new HashMap<>();

		List<String> skus = new ArrayList<>();
		for (SplitSalesOrder order : orders) {
			for (SplitSalesOrderItem splitSalesOrderItem : order.getSplitSalesOrderItems()) {
				SalesOrderItem salesOrderItem = splitSalesOrderItem.getSalesOrderItem();
				if (salesOrderItem.getParentOrderItem() == null) {
					skus.add(salesOrderItem.getSku());
				}
			}
		}

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

		GetProductsBySkuRequest getProductsBySkuRequest = new GetProductsBySkuRequest();
		getProductsBySkuRequest.setSkus(skus);
		getProductsBySkuRequest.setSearchVariants(true);

		HttpEntity<GetProductsBySkuRequest> requestBody = new HttpEntity<>(getProductsBySkuRequest, requestHeaders);
		String url = mulinBaseUrl + "/v1/products/productsBySku";

		try {
			LOGGER.info("mulin requst URL:" + url);
			LOGGER.info("mulin request body:" + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<GetProductsBySkuResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					GetProductsBySkuResponse.class);

			GetProductsBySkuResponse body = response.getBody();
			LOGGER.info("mulin response body:" + mapper.writeValueAsString(body));
			if (response.getStatusCode() == HttpStatus.OK && body != null) {
				productsFromMulin = body.getResponse();

				for (Map.Entry<String, ProductResponseBody> entry : productsFromMulin.entrySet()) {
					productsByIdFromMulin.put(entry.getKey(), entry.getValue());
				}
			}
		} catch (RestClientException e) {
			LOGGER.error("exception occurred during mulin call:" + e.getMessage());
		}catch (Exception ex) {
			LOGGER.error("exception occurred during mulin call:" + ex.getMessage());
		}

		return productsByIdFromMulin;
	}
    
    public RetrieveRatingsResponse retrieveRatings(RetrieveRatingsRequest customerRatings, RestTemplate restTemplate) {

    	RetrieveRatingsResponse customerRatingsResponse = new RetrieveRatingsResponse();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

		HttpEntity<RetrieveRatingsRequest> requestBody = new HttpEntity<>(customerRatings, requestHeaders);
		String url = mulinBaseUrl + "/v1/ratings/list";

		try {
			ResponseEntity<RetrieveRatingsResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					RetrieveRatingsResponse.class);
			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
				customerRatingsResponse = response.getBody();
			}
		} catch (RestClientException e) {
			LOGGER.error("exception occurred during mulin call while retrieve ratings :" + e.getMessage());
		} catch (Exception ex) {
			LOGGER.error("exception occurred during mulin call while retrieve ratings :" + ex.getMessage());
		}

		return customerRatingsResponse;
	}
    
	public RatingsResponse saveRatings(CustomerRatings customerRatings, RestTemplate restTemplate) {

		RatingsResponse ratingsResponse = new RatingsResponse();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

		HttpEntity<CustomerRatings> requestBody = new HttpEntity<>(customerRatings, requestHeaders);
		String url = mulinBaseUrl + MULIN_RATIN_UPDATE;

		try {
			ResponseEntity<RatingsResponse> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					RatingsResponse.class);
			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
				ratingsResponse = response.getBody();

			}
		} catch (RestClientException e) {
			LOGGER.error("exception occurred during mulin call while saving ratings :" + e.getMessage());
		} catch (Exception ex) {
			LOGGER.error("exception occurred during mulin call while saving ratings :" + ex.getMessage());
		}

		return ratingsResponse;
	}
	
	public RatingsResponse updateRatings(UpdateCustomerRatingsReq customerRatings, RestTemplate restTemplate) {

		RatingsResponse ratingsResponse = new RatingsResponse();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

		HttpEntity<UpdateCustomerRatingsReq> requestBody = new HttpEntity<>(customerRatings, requestHeaders);
		String url = mulinBaseUrl + MULIN_RATIN_UPDATE;

		try {
			ResponseEntity<RatingsResponse> response = restTemplate.exchange(url, HttpMethod.PUT, requestBody,
					RatingsResponse.class);
			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
				ratingsResponse = response.getBody();

			}
		} catch (RestClientException e) {
			LOGGER.error("exception occurred during mulin call while updating ratings :" + e.getMessage());
		} catch (Exception ex) {
			LOGGER.error("exception occurred during mulin call while updating ratings :" + ex.getMessage());
		}

		return ratingsResponse;
	}
	
	public RatingsResponse deleteRatings(DeleteCustomerRatingsReq customerRatings, RestTemplate restTemplate) {

		RatingsResponse ratingsResponse = new RatingsResponse();
		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add(USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);

		HttpEntity<DeleteCustomerRatingsReq> requestBody = new HttpEntity<>(customerRatings, requestHeaders);
		String url = mulinBaseUrl + MULIN_RATIN_UPDATE;

		try {
			ResponseEntity<RatingsResponse> response = restTemplate.exchange(url, HttpMethod.DELETE, requestBody,
					RatingsResponse.class);
			if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
				ratingsResponse = response.getBody();

			}
		} catch (RestClientException e) {
			LOGGER.error("exception occurred during mulin call while deleting ratings :" + e.getMessage());
		} catch (Exception ex) {
			LOGGER.error("exception occurred during mulin call while deleting ratings :" + ex.getMessage());
		}

		return ratingsResponse;
	}

}
