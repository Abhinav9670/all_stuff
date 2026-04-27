package org.styli.services.customer.service.impl;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.repository.CustomerEntityMysqlRepository;
import org.styli.services.customer.repository.Customer.CustomerEntityRepository;
import org.styli.services.customer.utility.Constants;

/**
 * @author Swapna Mahajan (Swapna.Mahajan@landmarkgroup.com)
 *
 */
@Component
@EnableAsync
public class AsyncService {
	
	private static final Log LOGGER = LogFactory.getLog(AsyncService.class);
	
    @Value("${order.ribbon.listOfServers}")
    private String orderServiceBaseUrl;
    
    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;
    
    @Autowired
    CustomerEntityMysqlRepository customerEntityMysqlRepository;
    
    @Autowired
    CustomerEntityRepository customerEntityRepository;
    
	@Async
	public CompletableFuture<String> asyncSalesOrdersUpdateCustId(final Customer savedCustomer) {
		try {
		    HttpHeaders requestHeaders = new HttpHeaders();
			requestHeaders.setContentType(MediaType.APPLICATION_JSON);
			requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

			HttpEntity<Customer> requestBody = new HttpEntity<>(savedCustomer,requestHeaders);
			String url = "http://" + orderServiceBaseUrl + "/salesorder/findsalesorders";

			LOGGER.info("customer Id :"+savedCustomer.getCustomerId());
			restTemplate.exchange(url, HttpMethod.POST, requestBody, Void.class);
			
		} catch (Exception e) {

			LOGGER.error("Exception occurred: " + e.getMessage());
		}
		
		
		return null;
	}
}
