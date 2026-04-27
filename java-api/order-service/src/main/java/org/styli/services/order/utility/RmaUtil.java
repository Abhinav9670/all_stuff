package org.styli.services.order.utility;

import java.util.Collections;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.pojo.InventoryMapping;
import org.styli.services.order.pojo.order.WMSReturnCancelRequest;
import org.styli.services.order.service.impl.SalesOrderServiceV3Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


@Component
public class RmaUtil {
	
	@Autowired
	RestTemplate restTemplate;

	private static final Log LOGGER = LogFactory.getLog(RmaUtil.class);

	private static final ObjectMapper mapper = new ObjectMapper();

	public void pushReturnCancelToWms(WMSReturnCancelRequest payload) {
		try {
			HttpHeaders headers = new HttpHeaders();
			LOGGER.info("inside wms push return cancel rest controller");
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
			headers.add(Constants.HEADER_USER_AGENT, Constants.USER_AGENT_FOR_REST_CALLS);
			InventoryMapping inventoryMapping = Constants.orderCredentials.getInventoryMapping().get(0);
			headers.add(Constants.WMS_USER_HEADER_NAME, inventoryMapping.getWmsHeaderUsrName());
			headers.add(Constants.WMS_USER_HEADER_PASSWORD, inventoryMapping.getWmsHeaderUsrPassword());
			HttpEntity<WMSReturnCancelRequest> requestBody = new HttpEntity<>(payload, headers);

			String url = Constants.orderCredentials.getOrderDetails().getWmsUrl() + "/return/cancel-items";

			ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.PUT, requestBody, Object.class);

			Object responseBody = response.getBody();
			LOGGER.info("wms return cancel push url:" + url);
			LOGGER.info("wms return cancel request body" + mapper.writeValueAsString(requestBody));
			LOGGER.info("wms return cancel response body" + mapper.writeValueAsString(responseBody));
			if (response.getStatusCode() == HttpStatus.OK) {

				LOGGER.info("return wms cancel sent for:" + mapper.writeValueAsString(requestBody));
			}

		} catch (JsonProcessingException e) {
			LOGGER.info("wms return cancel error:", e);
		}
	}

}
