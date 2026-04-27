package org.styli.services.order.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SubSalesOrder;
import org.styli.services.order.pojo.CustomCouponRedemptionV5Request;
import org.styli.services.order.pojo.GenericApiResponse;
import org.styli.services.order.pojo.QuoteDTO;
import org.styli.services.order.pojo.response.CustomCouponRedemptionV5Response;
import org.styli.services.order.pojo.whatsapp.bot.MobileReturnDetailResponse;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.consulValues.PromoRedemptionValues;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PromoService {

	private static final Log LOGGER = LogFactory.getLog(EASServiceImpl.class);
	
	private static final ObjectMapper mapper = new ObjectMapper();

	@Value("${auth.internal.header.bearer.token}")
	private String internalHeaderBearerToken;

	@Autowired
	private SalesOrderRepository salesOrderRepository;

	@Autowired
	ConfigService configService;
	
	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Async("asyncExecutor")
	public GenericApiResponse<MobileReturnDetailResponse> syncPromoRedemptionMissed(String authorizationToken) {
		GenericApiResponse<MobileReturnDetailResponse> finalResponse = new GenericApiResponse<>();
		try {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				finalResponse.setStatus(false);
				finalResponse.setStatusCode("401");
				finalResponse.setStatusMsg(Constants.MISSING_OR_WRONG_TOKEN);
				return finalResponse;
			}

			LocalDate yesterday = LocalDate.now().minusDays(1);
			Date yesterdayDate = Date.from(yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant());

			List<SalesOrder> salesOrders = salesOrderRepository.findOrderRedemptionMissed(yesterdayDate);

			for (SalesOrder order : salesOrders) {
				reedmeExternalCoupon(order);
			}

		} catch (Exception e) {
			LOGGER.error("Earn Kafka Error publishSaleOrderToKafka.", e);
		}
		return finalResponse;
	}

	public boolean reedmeExternalCoupon(SalesOrder order) {

		HttpHeaders requestHeaders = new HttpHeaders();
		requestHeaders.setContentType(MediaType.APPLICATION_JSON);
		requestHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		requestHeaders.add("user-agent", Constants.USER_AGENT_FOR_REST_CALLS);

		CustomCouponRedemptionV5Request payload = new CustomCouponRedemptionV5Request();
		if (null != order.getCustomerId()) {
			payload.setCustomerEmailId(order.getCustomerId().toString());
		} else {
			payload.setCustomerEmailId(order.getCustomerEmail());
		}
		String QuoteId = (null != order.getSubSalesOrder().getExternalQuoteId()) ? String.valueOf(order.getSubSalesOrder().getExternalQuoteId()) : "";
		
		payload.setOrderReferenceId(order.getIncrementId());
		payload.setQuoteId(QuoteId);
		
		if (null != order.getStoreId()) {
			payload.setStoreId(order.getStoreId().toString());
		}
		HttpEntity<CustomCouponRedemptionV5Request> requestBody = new HttpEntity<>(payload, requestHeaders);

		String url = Constants.getPromoRedemptionUrl().getRedemptionEndpoint();

		Map<String, Object> parameters = new HashMap<>();

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);

		LOGGER.info("Redemption Coupon URl:" + builder.buildAndExpand(parameters).toUri());
		SubSalesOrder subSalesOrder = null;
		try {
			LOGGER.info("request Body:" + mapper.writeValueAsString(requestBody.getBody()));
			ResponseEntity<CustomCouponRedemptionV5Response> response = restTemplate.exchange(
					builder.buildAndExpand(parameters).toUri(), HttpMethod.POST, requestBody,
					CustomCouponRedemptionV5Response.class);

			if (response.getStatusCode() == HttpStatus.OK) {
				LOGGER.info("response Body:" + mapper.writeValueAsString(response.getBody()));
				CustomCouponRedemptionV5Response body = response.getBody();
				if (body != null && body.getCode() == 200) {
					LOGGER.info("Coupon redemption response: " + response.getBody());
					String redmeptionTrackingId = body.getTrackingId();

					if (StringUtils.isNoneBlank(redmeptionTrackingId)) {
						subSalesOrder = order.getSubSalesOrder();
						if (null != subSalesOrder) {
							subSalesOrder.setExternalCouponRedemptionTrackingId(redmeptionTrackingId);
							subSalesOrder.setExternalCouponRedemptionStatus(1);
							order.setSubSalesOrder(subSalesOrder);
							subSalesOrder.setSalesOrder(order);
							salesOrderRepository.saveAndFlush(order);
							return true;
						}
					}
				}
			}
		} catch (RestClientException | JsonProcessingException e) {
			LOGGER.error("Exception occurred  during PROMO REST call:" + e.getMessage());
			return false;
		}
		return false;
	}
}
