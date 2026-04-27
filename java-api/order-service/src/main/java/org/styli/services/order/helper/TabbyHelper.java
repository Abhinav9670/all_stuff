package org.styli.services.order.helper;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.pojo.PaymentDTO;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.TabbyDetails;
import org.styli.services.order.pojo.tabby.Order;
import org.styli.services.order.pojo.tabby.TabbyItems;
import org.styli.services.order.pojo.tabby.TabbyPayment;
import org.styli.services.order.pojo.tabby.TabbyRefundDTO;
import org.styli.services.order.pojo.tabby.TabbypaymentCaptureDTO;
import org.styli.services.order.service.AutoRefundService;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.PaymentUtility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;


/**
 * Tabby API's
 * 
 * @author Chandan Behera
 *
 */
@Component
public class TabbyHelper {

	private static final Log LOGGER = LogFactory.getLog(TabbyHelper.class);
	private static final String AUTHORIZATION = "Authorization";
	private static final String BEARER = "Bearer ";
	private static final String TABBY_PAYMENTS_ENDPOINT = "/v1/payments/";
	private static final String STATUS_PAYMENTS_ENDPOINT = "/v2/payments/";
	private static final String RESPONSE = " Response : ";
	private static final String REQUEST = " Request : ";
	private static final String ORDERID = " Order ID : ";
	private static final String PAYMENTID = " Payment ID : ";

	/** KSA store IDs (1 = KSA English, 3 = KSA Arabic). v2 API for these stores uses tabby_ksa_base_url from Consul. */
	private static final List<Integer> KSA_STORE_IDS = Collections.unmodifiableList(Arrays.asList(1, 3));
	
	private static final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	@Qualifier("restTemplateBuilder")
	private RestTemplate restTemplate;

	@Value("${tabby.payment.secretkey}")
	private String tabbySecretKey;
	
	@Autowired
	PaymentUtility paymentUtility;
	
	/**
	 * Tabby Refund Payment
	 * 
	 * @param payload
	 * @return
	 *
	 */
	public RefundPaymentRespone refundPayment(SalesOrder order, String refundAmount) {
		RefundPaymentRespone response = new RefundPaymentRespone();
		String paymentId = order.getSubSalesOrder().getPaymentId();
		String requestPayload = "";
		try {
			TabbyRefundDTO payload = tabbyRefundPayload(order, refundAmount);
			requestPayload = new Gson().toJson(payload);
			String url = tabbyUrl() + TABBY_PAYMENTS_ENDPOINT + paymentId + "/refunds";
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tabbySecretKey);
			HttpEntity<TabbyRefundDTO> requestBody = new HttpEntity<>(payload, headers);
			ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody, String.class);
			LOGGER.info("Tabby Refund for Order Success, Order ID: " + order.getEntityId() + PAYMENTID + paymentId + REQUEST + requestPayload + RESPONSE + exchange.getBody());
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Refunded Successfully!");
		} catch (Exception e) {
			LOGGER.error("Tabby Refund for Order Error,  Order ID:" + order.getEntityId() + PAYMENTID + paymentId + REQUEST + requestPayload + RESPONSE + e);
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(e.getMessage());
		}
		return response;
	}

	/**
	 * Tabby Refund Payment
	 *
	 * @param payload
	 * @return
	 *
	 */
	public RefundPaymentRespone refundSplitPayment(SplitSalesOrder splitSalesOrder, String refundAmount) {
		RefundPaymentRespone response = new RefundPaymentRespone();
		String paymentId = splitSalesOrder.getSplitSubSalesOrder().getPaymentId();
		String requestPayload = "";
		try {
			TabbyRefundDTO payload = tabbySplitRefundPayload(splitSalesOrder, refundAmount);
			requestPayload = new Gson().toJson(payload);
			String url = tabbyUrl() + TABBY_PAYMENTS_ENDPOINT + paymentId + "/refunds";
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tabbySecretKey);
			HttpEntity<TabbyRefundDTO> requestBody = new HttpEntity<>(payload, headers);
			ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody, String.class);
			LOGGER.info("Tabby Refund for Order Success, Order ID: " + splitSalesOrder.getEntityId() + PAYMENTID + paymentId + REQUEST + requestPayload + RESPONSE + exchange.getBody());
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Refunded Successfully!");
		} catch (Exception e) {
			LOGGER.error("Tabby Refund for Order Error,  Order ID:" + splitSalesOrder.getEntityId() + PAYMENTID + paymentId + REQUEST + requestPayload + RESPONSE + e);
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(e.getMessage());
		}
		return response;
	}


	private TabbyRefundDTO tabbyRefundPayload(SalesOrder order, String amount) {
		TabbyRefundDTO payload = new TabbyRefundDTO();
		List<TabbyItems> items = order.getSalesOrderItem().stream()
				.filter(item -> StringUtils.equalsAnyIgnoreCase(item.getProductType(), "simple")).map(item -> {
					BigDecimal qty = item.getQtyOrdered();
					String name = item.getName();
					String price = amountToTwoDecimalPlace(item.getPrice());
					String description = item.getDescription();
					return new TabbyItems(name, description, qty.intValue(), price);
				}).collect(Collectors.toList());
		payload.setItems(items);
		
		payload.setAmount(amountToTwoDecimalPlace(new BigDecimal(amount)));
		return payload;
	}

	private TabbyRefundDTO tabbySplitRefundPayload(SplitSalesOrder splitSalesOrder, String amount) {
		TabbyRefundDTO payload = new TabbyRefundDTO();
		List<TabbyItems> items = splitSalesOrder.getSplitSalesOrderItems().stream()
				.filter(item -> StringUtils.equalsAnyIgnoreCase(item.getProductType(), "simple")).map(item -> {
					BigDecimal qty = item.getQtyOrdered();
					String name = item.getName();
					String price = amountToTwoDecimalPlace(item.getPrice());
					String description = item.getDescription();
					return new TabbyItems(name, description, qty.intValue(), price);
				}).collect(Collectors.toList());
		payload.setItems(items);

		payload.setAmount(amountToTwoDecimalPlace(new BigDecimal(amount)));
		return payload;
	}

	/** Base URL for v1 APIs (and v2 when not KSA). All endpoints use this unless v2 + KSA store. */
	private String tabbyUrl() {
		return getTabbyBaseUrl();
	}

	private String getTabbyBaseUrl() {
		if (null != Constants.orderCredentials) {
			TabbyDetails tabby = Constants.orderCredentials.getTabby();
			return tabby != null ? tabby.getTabbyBaseUrl() : null;
		}
		return null;
	}

	private String getTabbyKsaBaseUrl() {
		if (null != Constants.orderCredentials) {
			TabbyDetails tabby = Constants.orderCredentials.getTabby();
			return tabby != null ? tabby.getTabbyKsaBaseUrl() : null;
		}
		return null;
	}

	/** For v2 API: use KSA base URL when store is KSA and tabby_ksa_base_url is set in Consul; otherwise default base URL. */
	private String tabbyUrlForV2(Integer storeId) {
		if (storeId != null && isKsaStore(storeId)) {
			String ksaUrl = getTabbyKsaBaseUrl();
			if (StringUtils.isNotBlank(ksaUrl)) {
				return ksaUrl;
			}
		}
		return getTabbyBaseUrl();
	}

	private boolean isKsaStore(Integer storeId) {
		return storeId != null && KSA_STORE_IDS.contains(storeId);
	}
	
	
	public String retrievePayment(String paymentId) {
		return retrievePayment(paymentId, null);
	}

	/**
	 * Retrieve payment by ID (v2 API). For KSA stores, uses tabby_ksa_base_url from Consul when set.
	 * @param paymentId Tabby payment id
	 * @param storeId   Store id (optional); when 1 or 3 (KSA), KSA base URL is used if configured
	 */
	public String retrievePayment(String paymentId, Integer storeId) {
		try {
			String baseUrl = tabbyUrlForV2(storeId);
			if (baseUrl == null) {
				baseUrl = tabbyUrl();
			}
			String url = baseUrl + STATUS_PAYMENTS_ENDPOINT + paymentId;
			LOGGER.info("tabby retive paymnet url:"+url);
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tabbySecretKey);
			HttpEntity<String> requestBody = new HttpEntity<>(null, headers);
			
			LOGGER.info("tabby retive paymnet request body:"+mapper.writeValueAsString(requestBody));
			
			ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.GET, requestBody, String.class);
			LOGGER.info("Tabby Retrieve Payment Success-checked, Payment ID: " + paymentId + RESPONSE + exchange.getBody());
			return exchange.getBody();
		} catch (Exception e) {
			LOGGER.error("Tabby Retrieve Payment Error, Payment ID: " + paymentId + RESPONSE + e);
		}
		return null;
	}


	public boolean updateOrderId(String orderId, String paymentId) {
		String requestPayload = "";
		try {
			TabbyPayment payload = tabbyupdateOrderPayload(orderId);
			requestPayload = new Gson().toJson(payload);
			String url = tabbyUrl() + TABBY_PAYMENTS_ENDPOINT + paymentId;
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tabbySecretKey);
			
			LOGGER.info("tabby update order url:"+url);
			
			
			HttpEntity<TabbyPayment> requestBody = new HttpEntity<>(payload, headers);
			
			LOGGER.info("tabby update order id:"+mapper.writeValueAsString(requestBody));

			ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.PUT, requestBody, String.class);
			
			LOGGER.info("Tabby Update Order Succcess, Payment ID: " + paymentId + ORDERID + orderId + REQUEST + requestPayload + RESPONSE + exchange.getBody());
			if(exchange.getStatusCodeValue()==200) {
				return true;
			}
		} catch (Exception e) {
			LOGGER.error("Tabby Update Order Error, Payment ID: " + paymentId + ORDERID + orderId + " Error : " + e);
		}
		return false;
	}

	private TabbyPayment tabbyupdateOrderPayload(String orderId) {
		TabbyPayment payLoad=new TabbyPayment();
		Order order=new Order();
		order.setReferenceId(orderId);
		payLoad.setOrder(order);
		return payLoad;
	}

	public boolean capturePayment(String paymentId,SalesOrder order,boolean isNotProxy) {
		String requestPayload = "";
		try {
			TabbypaymentCaptureDTO payload = tabbyPaymentCapturePayload(order,isNotProxy);
			requestPayload = new Gson().toJson(payload);
			String url = tabbyUrl() + TABBY_PAYMENTS_ENDPOINT + paymentId + "/captures";
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tabbySecretKey);
			HttpEntity<TabbypaymentCaptureDTO> requestBody = new HttpEntity<>(payload, headers);
			
			LOGGER.info("tabby capture payment url:"+url);
			LOGGER.info("tabby capture payment request body:"+mapper.writeValueAsString(requestBody));
			
			ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody, String.class);
			LOGGER.info("Tabby Capture Payment Succcess, Payment ID: " + paymentId + " Order ID: " + order.getEntityId() + REQUEST + requestPayload + RESPONSE+ exchange.getBody());
			return true;
		} catch (Exception e) {
			LOGGER.info("Tabby Capture Payment Error, Payment ID: " + paymentId + " Order ID: " + order.getEntityId() + REQUEST + requestPayload + RESPONSE + e);
			/** call get payment to check **/
			String paymentDeatils = retrievePayment(paymentId, order.getStoreId());
			if (Objects.nonNull(paymentDeatils))
				return paymentUtility.buildPaymentDTOForTabbyCapture(paymentDeatils);
		}
		return false;
	}

	public boolean capturePaymentV2(String paymentId, SplitSalesOrder splitSalesOrder, boolean isNotProxy) {
		String requestPayload = "";
		try {
			TabbypaymentCaptureDTO payload = tabbyPaymentCapturePayloadV2(splitSalesOrder,isNotProxy);
			requestPayload = new Gson().toJson(payload);
			String url = tabbyUrl() + TABBY_PAYMENTS_ENDPOINT + paymentId + "/captures";
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tabbySecretKey);
			HttpEntity<TabbypaymentCaptureDTO> requestBody = new HttpEntity<>(payload, headers);

			LOGGER.info("tabby capture payment url:"+url);
			LOGGER.info("tabby capture payment request body:"+mapper.writeValueAsString(requestBody));

			ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody, String.class);
			LOGGER.info("Tabby Capture Payment Succcess, Payment ID: " + paymentId + " split order id: " + splitSalesOrder.getEntityId() + REQUEST + requestPayload + RESPONSE+ exchange.getBody());
			return true;
		} catch (Exception e) {
			LOGGER.info("Tabby Capture Payment Error, Payment ID: " + paymentId + " split order id: " + splitSalesOrder.getEntityId() + REQUEST + requestPayload + RESPONSE + e);
			/** call get payment to check **/
			String paymentDeatils = retrievePayment(paymentId, splitSalesOrder.getStoreId());
			if (Objects.nonNull(paymentDeatils))
				return paymentUtility.buildPaymentDTOForTabbyCapture(paymentDeatils);
		}
		return false;
	}

	private TabbypaymentCaptureDTO tabbyPaymentCapturePayload(SalesOrder order, boolean isNotProxy) {
		if(isNotProxy) {
			TabbypaymentCaptureDTO payLoad =new TabbypaymentCaptureDTO();
			String amount = amountToTwoDecimalPlace(order.getGrandTotal());
			payLoad.setAmount(amount);
			return payLoad;
		}else {
			TabbypaymentCaptureDTO payLoad =new TabbypaymentCaptureDTO();
			String amount = amountToTwoDecimalPlace(paymentUtility.getCaptureAmount(order));
			payLoad.setAmount(amount);
			return payLoad;
		}
	}

	private TabbypaymentCaptureDTO tabbyPaymentCapturePayloadV2(SplitSalesOrder splitSalesOrder, boolean isNotProxy) {
		if(isNotProxy) {
			TabbypaymentCaptureDTO payLoad =new TabbypaymentCaptureDTO();
			String amount = amountToTwoDecimalPlace(splitSalesOrder.getGrandTotal());
			payLoad.setAmount(amount);
			return payLoad;
		}else {
			TabbypaymentCaptureDTO payLoad =new TabbypaymentCaptureDTO();
			String amount = amountToTwoDecimalPlace(paymentUtility.getCaptureAmountV2(splitSalesOrder));
			payLoad.setAmount(amount);
			return payLoad;
		}
	}
	
	public TabbyPayment closePayment(String paymentId) {
		try {
			String url = tabbyUrl() + TABBY_PAYMENTS_ENDPOINT + paymentId + "/close";
			LOGGER.info("tabby close paymnet url:"+url);
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tabbySecretKey);
			HttpEntity<String> requestBody = new HttpEntity<>(null, headers);
			
			LOGGER.info("Tabby Close paymnet request body:"+mapper.writeValueAsString(requestBody));
			
			ResponseEntity<TabbyPayment> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody, TabbyPayment.class);
			TabbyPayment body = exchange.getBody();
			LOGGER.info("Tabby Close Payment Success-checked, Payment ID: " + paymentId + RESPONSE +  mapper.writeValueAsString(body));
			if(Objects.nonNull(body))
				body.setSuccess(true);
			return body;
		} catch (Exception e) {
			try {
				LOGGER.error("Tabby Close Payment Error, Payment ID: " + paymentId + RESPONSE +  mapper.writeValueAsString(e));
			} catch (JsonProcessingException e1) {
				LOGGER.error("Tabby Close Payment Error, Payment ID: " + paymentId);
			}
			TabbyPayment res = new TabbyPayment();
			res.setStatus(e.getMessage());
			res.setSuccess(false);
			return res;
		}
	}

	/**
	 * Convert an amount to two decimal place
	 * @param amount
	 * @return
	 */
	private String amountToTwoDecimalPlace(BigDecimal amount) {
		DecimalFormat df = new DecimalFormat("###.##");
		return df.format(amount);
	}

	
}
