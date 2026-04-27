package org.styli.services.order.helper;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Optional;

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
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrderPayment;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.TamaraDetails;
import org.styli.services.order.pojo.tamara.Amount;
import org.styli.services.order.pojo.tamara.TamaraCaptures;
import org.styli.services.order.pojo.tamara.TamaraPayment;
import org.styli.services.order.pojo.tamara.TamaraRefunds;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.PaymentUtility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

/**
 * Tamara API's
 * 
 * @author Maninee Mahapatra
 *
 */
@Component
public class TamaraHelper {
	private static final Log LOGGER = LogFactory.getLog(TamaraHelper.class);
	private static final String AUTHORIZATION = "Authorization";
	private static final String BEARER = "Bearer ";
	private static final String TAMARA_STATUS_ENDPOINT = "/orders/";
	private static final String TAMARA_PAYMENTS_ENDPOINT = "/payments/";
	private static final String RESPONSE = " Response : ";
	private static final String REQUEST = " Request : ";
	private static final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	@Qualifier("restTemplateBuilder")
	private RestTemplate restTemplate;

	@Value("${tamara.payment.secretkey}")
	private String tamaraSecretKey;
	
	@Autowired
	PaymentUtility paymnetUtility;
	
	public String retrievePayment(String orderReferenceId) {
		try {
			String url = baseUrl() + TAMARA_STATUS_ENDPOINT + orderReferenceId;
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tamaraSecretKey);
			HttpEntity<String> requestBody = new HttpEntity<>(null, headers);
			LOGGER.info("Tamara get order detailst request body:" + mapper.writeValueAsString(requestBody) + " URL: " + url);

			ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.GET, requestBody, String.class);
			LOGGER.info("Tamara Retrieve Payment Success-checked, Order ID: " + orderReferenceId + RESPONSE
					+ exchange.getBody());
			return exchange.getBody();
		} catch (Exception e) {
			LOGGER.error("Tamara get order details by order referenceId  Error, Order Reference ID: " + orderReferenceId
					+ RESPONSE + e);
		}
		return null;

	}

	public String authorisedOrder(String orderId) {
		try {
			String url = baseUrl() + TAMARA_STATUS_ENDPOINT + orderId + "/authorise";
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tamaraSecretKey);
			HttpEntity<String> requestBody = new HttpEntity<>(null, headers);
			LOGGER.info(
					"Tamara authorised order request body:" + mapper.writeValueAsString(requestBody) + " URL: " + url);
			ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody, String.class);
			LOGGER.info("Tamara Authorise Order Success-checked, Order ID: " + orderId + RESPONSE + exchange.getBody());
			return exchange.getBody();
		} catch (Exception e) {
			LOGGER.error("Tamara get authorised order by orderId  Error, Order ID: " + orderId + RESPONSE + e);
		}
		return null;
	}

	/**
	 * Capture the amount.
	 * @param paymentId
	 * @param order
	 * @return
	 */
	public TamaraCaptures capturePayment(String paymentId, SalesOrder order) {
		String requestPayload = "";
		try {
			TamaraPayment payload = capturePayload(paymentId, order);
			requestPayload = new Gson().toJson(payload);
			String url = baseUrl() + TAMARA_PAYMENTS_ENDPOINT + "capture";
			LOGGER.info("Tamara capture order by orderId  url:" + url);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tamaraSecretKey);
			HttpEntity<TamaraPayment> requestBody = new HttpEntity<>(payload, headers);

			LOGGER.info("Tamara capture order request body:" + mapper.writeValueAsString(requestBody));

			ResponseEntity<TamaraCaptures> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					TamaraCaptures.class);

			LOGGER.info("Tamara Capture Order Success, Order ID: " + paymentId  + " " + order.getIncrementId() + REQUEST + requestPayload + RESPONSE
					+ response.getBody());
			LOGGER.info("Tamara Capture API Response" + mapper.writeValueAsString(response.getBody()));
			return response.getBody();
		} catch (Exception e) {
			LOGGER.error("Tamara capture order by orderId  Error, Order ID: " + paymentId + " " + order.getIncrementId() + REQUEST + requestPayload + " Error : " + e);
		}
		return null;
	}

	/**
	 * Capture the amount.
	 * @param paymentId
	 * @param splitSalesOrder
	 * @return
	 */
	public TamaraCaptures capturePaymentV2(String paymentId, SplitSalesOrder splitSalesOrder) {
		String requestPayload = "";
		try {
			TamaraPayment payload = capturePayloadV2(paymentId, splitSalesOrder);
			requestPayload = new Gson().toJson(payload);
			String url = baseUrl() + TAMARA_PAYMENTS_ENDPOINT + "capture";
			LOGGER.info("Tamara capture order by split order id  url:" + url);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tamaraSecretKey);
			HttpEntity<TamaraPayment> requestBody = new HttpEntity<>(payload, headers);

			LOGGER.info("Tamara capture order request body:" + mapper.writeValueAsString(requestBody));

			ResponseEntity<TamaraCaptures> response = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					TamaraCaptures.class);

			LOGGER.info("Tamara Capture Order Success, split order id: " + paymentId  + " " + splitSalesOrder.getIncrementId() + REQUEST + requestPayload + RESPONSE
					+ response.getBody());
			LOGGER.info("Tamara Capture API Response" + mapper.writeValueAsString(response.getBody()));
			return response.getBody();
		} catch (Exception e) {
			LOGGER.error("Tamara capture order by split order id  Error, split order id: " + paymentId + " " + splitSalesOrder.getIncrementId() + REQUEST + requestPayload + " Error : " + e);
		}
		return null;
	}

	private TamaraPayment capturePayload(String paymentId, SalesOrder order) {
		TamaraPayment payLoad = new TamaraPayment();
		payLoad.setOrderId(paymentId);
		Amount amount = new Amount();
		String grandTotal = amountToTwoDecimalPlace(paymnetUtility.getCaptureAmount(order));
		amount.setTotalAmount(grandTotal);
		amount.setCurrency(order.getOrderCurrencyCode());
		payLoad.setTotalAmount(amount);
		return payLoad;
	}

	private TamaraPayment capturePayloadV2(String paymentId, SplitSalesOrder splitSalesOrder) {
		TamaraPayment payLoad = new TamaraPayment();
		payLoad.setOrderId(paymentId);
		Amount amount = new Amount();
		String grandTotal = amountToTwoDecimalPlace(paymnetUtility.getCaptureAmountV2(splitSalesOrder));
		amount.setTotalAmount(grandTotal);
		amount.setCurrency(splitSalesOrder.getOrderCurrencyCode());
		payLoad.setTotalAmount(amount);
		return payLoad;
	}
	


	/**
	 * Full or Partial Refund amount to customer
	 * @param order
	 * @param refundAmount
	 * @return
	 */
	public RefundPaymentRespone refundPayment(SalesOrder order, String refundAmount) {
		RefundPaymentRespone response = new RefundPaymentRespone();
		String requestPayload = "";
		try {
			TamaraPayment payload = tamaraRefundOrderPayload(order, refundAmount);
			requestPayload = new Gson().toJson(payload);
			String url = baseUrl() + TAMARA_PAYMENTS_ENDPOINT + "refund";
			LOGGER.info("Tamara refund order by orderId  url:" + url);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tamaraSecretKey);
			HttpEntity<TamaraPayment> requestBody = new HttpEntity<>(payload, headers);

			LOGGER.info("Tamara refud order request body:" + mapper.writeValueAsString(requestBody));

			ResponseEntity<TamaraPayment> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					TamaraPayment.class);

			LOGGER.info("Tamara refund Order Success, Order ID: " + order.getIncrementId()+ REQUEST + requestPayload + RESPONSE
					+ exchange.getBody());
			
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Refunded Successfully!");
			return response;
		} catch (Exception e) {
			LOGGER.error("Tamara refund order by orderId  Error, Order ID: " + order.getIncrementId() + RESPONSE + e);
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(e.getMessage());
		}
		return response;
	}

	/**
	 * Full or Partial Refund amount to customer
	 * @param order
	 * @param refundAmount
	 * @return
	 */
	public RefundPaymentRespone refundSplitPayment(SplitSalesOrder splitSalesOrder, String refundAmount) {
		RefundPaymentRespone response = new RefundPaymentRespone();
		String requestPayload = "";
		try {
			TamaraPayment payload = tamaraSplitRefundOrderPayload(splitSalesOrder, refundAmount);
			requestPayload = new Gson().toJson(payload);
			String url = baseUrl() + TAMARA_PAYMENTS_ENDPOINT + "refund";
			LOGGER.info("Tamara refund order by orderId  url:" + url);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tamaraSecretKey);
			HttpEntity<TamaraPayment> requestBody = new HttpEntity<>(payload, headers);

			LOGGER.info("Tamara refud order request body:" + mapper.writeValueAsString(requestBody));

			ResponseEntity<TamaraPayment> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					TamaraPayment.class);

			LOGGER.info("Tamara refund Order Success, Order ID: " + splitSalesOrder.getIncrementId()+ REQUEST + requestPayload + RESPONSE
					+ exchange.getBody());

			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Refunded Successfully!");
			return response;
		} catch (Exception e) {
			LOGGER.error("Tamara refund order by orderId  Error, Order ID: " + splitSalesOrder.getIncrementId() + RESPONSE + e);
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(e.getMessage());
		}
		return response;
	}

	private TamaraPayment tamaraRefundOrderPayload(SalesOrder order, String refundAmount) {
		TamaraPayment payLoad = new TamaraPayment();
		payLoad.setOrderId(order.getSubSalesOrder().getPaymentId());
		TamaraRefunds tamaraRefunds = new TamaraRefunds();
		Optional<SalesOrderPayment> orderPayment = order.getSalesOrderPayment().stream().findFirst();
		if(orderPayment.isPresent()) {
			SalesOrderPayment salesOrderPayment = orderPayment.get();
			tamaraRefunds.setCaptureId(salesOrderPayment.getCcTransId());
		}
		Amount amount = new Amount();
		amount.setTotalAmount(amountToTwoDecimalPlace(new BigDecimal(refundAmount)));
		amount.setCurrency(order.getOrderCurrencyCode());
		tamaraRefunds.setAmount(amount);
		payLoad.setRefunds(Arrays.asList(tamaraRefunds));
		return payLoad;
	}

	private TamaraPayment tamaraSplitRefundOrderPayload(SplitSalesOrder splitSalesOrder, String refundAmount) {
		TamaraPayment payLoad = new TamaraPayment();
		payLoad.setOrderId(splitSalesOrder.getSplitSubSalesOrder().getPaymentId());
		TamaraRefunds tamaraRefunds = new TamaraRefunds();
		Optional<SplitSalesOrderPayment> orderPayment = splitSalesOrder.getSplitSalesOrderPayments().stream().findFirst();
		if(orderPayment.isPresent()) {
			SplitSalesOrderPayment splitSalesOrderPayment = orderPayment.get();
			tamaraRefunds.setCaptureId(splitSalesOrderPayment.getCcTransId());
		}
		Amount amount = new Amount();
		amount.setTotalAmount(amountToTwoDecimalPlace(new BigDecimal(refundAmount)));
		amount.setCurrency(splitSalesOrder.getOrderCurrencyCode());
		tamaraRefunds.setAmount(amount);
		payLoad.setRefunds(Arrays.asList(tamaraRefunds));
		return payLoad;
	}

	public boolean updateOrderId(String incrementId, String orderId) {
		String requestPayload = "";
		try {
			TamaraPayment payload = tamaraupdateOrderReferenceIdPayload(incrementId);
			requestPayload = new Gson().toJson(payload);
			String url = baseUrl() + TAMARA_STATUS_ENDPOINT + orderId + "/reference-id";
			LOGGER.info("Tamara update order reference Id by orderId  url:" + url);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tamaraSecretKey);
			HttpEntity<TamaraPayment> requestBody = new HttpEntity<>(payload, headers);

			LOGGER.info("Tamara update order request body:" + mapper.writeValueAsString(requestBody));

			ResponseEntity<TamaraPayment> exchange = restTemplate.exchange(url, HttpMethod.PUT, requestBody,
					TamaraPayment.class);

			LOGGER.info("Tamara Update Order Reference Id Success, Order ID: " + orderId + REQUEST + requestPayload
					+ RESPONSE + exchange.getBody());
			LOGGER.info("Tamara Response" + mapper.writeValueAsString(exchange.getBody()));
			if (exchange.getStatusCodeValue() == 200) {
				return true;
			}
		} catch (Exception e) {
			LOGGER.error("Tamara Update order reference Id by orderId  Error, Order ID: " + orderId + RESPONSE + e);
		}
		return false;
	}

	private TamaraPayment tamaraupdateOrderReferenceIdPayload(String incrementId) {
		TamaraPayment payLoad = new TamaraPayment();
		payLoad.setOrderReferenceId(incrementId);
		return payLoad;
	}

	public RefundPaymentRespone cancelPayment(SalesOrder order, String amount) {
		String requestPayload = "";
		RefundPaymentRespone response = new RefundPaymentRespone();
		String paymentId = order.getSubSalesOrder().getPaymentId();
		try {
			TamaraPayment payload = tamaraCancelOrderPayload(order, amount);
			
			requestPayload = new Gson().toJson(payload);
			String url = baseUrl() + TAMARA_STATUS_ENDPOINT + paymentId +  "/cancel";
			LOGGER.info("Tamara cancel order by orderId  url:" + url);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tamaraSecretKey);
			HttpEntity<TamaraPayment> requestBody = new HttpEntity<>(payload, headers);

			LOGGER.info("Tamara cancel order request body:" + mapper.writeValueAsString(requestBody));

			ResponseEntity<TamaraPayment> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					TamaraPayment.class);

			LOGGER.info("Tamara Cancel Order Success, Payment ID: " + paymentId + REQUEST + requestPayload + RESPONSE
					+ exchange.getBody());
			LOGGER.info("Tamara Response" + mapper.writeValueAsString(exchange.getBody()));
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Refunded Successfully!");
		} catch (Exception e) {
			LOGGER.error("Tamara cancel order by orderId  Error, Payment ID: " + paymentId + REQUEST + requestPayload + " Error : " + e);
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(e.getMessage());
		}
		return response;
	}



	public RefundPaymentRespone cancelSplitPayment(SplitSalesOrder splitSalesOrder, String amount) {
		String requestPayload = "";
		RefundPaymentRespone response = new RefundPaymentRespone();
		String paymentId = splitSalesOrder.getSplitSubSalesOrder().getPaymentId();
		try {
			TamaraPayment payload = tamaraSplitCancelOrderPayload(splitSalesOrder, amount);

			requestPayload = new Gson().toJson(payload);
			String url = baseUrl() + TAMARA_STATUS_ENDPOINT + paymentId +  "/cancel";
			LOGGER.info("Tamara cancel order by orderId  url:" + url);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.add(AUTHORIZATION, BEARER + tamaraSecretKey);
			HttpEntity<TamaraPayment> requestBody = new HttpEntity<>(payload, headers);

			LOGGER.info("Tamara cancel order request body:" + mapper.writeValueAsString(requestBody));

			ResponseEntity<TamaraPayment> exchange = restTemplate.exchange(url, HttpMethod.POST, requestBody,
					TamaraPayment.class);

			LOGGER.info("Tamara Cancel Order Success, Payment ID: " + paymentId + REQUEST + requestPayload + RESPONSE
					+ exchange.getBody());
			LOGGER.info("Tamara Response" + mapper.writeValueAsString(exchange.getBody()));
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Refunded Successfully!");
		} catch (Exception e) {
			LOGGER.error("Tamara cancel order by orderId  Error, Payment ID: " + paymentId + REQUEST + requestPayload + " Error : " + e);
			response.setStatus(false);
			response.setStatusCode("205");
			response.setStatusMsg(e.getMessage());
		}
		return response;
	}


	private TamaraPayment tamaraCancelOrderPayload(SalesOrder order, String amountToRefund) {
		TamaraPayment payLoad = new TamaraPayment();
		Amount amount = new Amount();
		amount.setTotalAmount(amountToTwoDecimalPlace(new BigDecimal(amountToRefund)));
		amount.setCurrency(order.getStoreCurrencyCode());
		payLoad.setTotalAmount(amount);
		return payLoad;
	}

	private TamaraPayment tamaraSplitCancelOrderPayload(SplitSalesOrder splitSalesOrder, String amountToRefund) {
		TamaraPayment payLoad = new TamaraPayment();
		Amount amount = new Amount();
		amount.setTotalAmount(amountToTwoDecimalPlace(new BigDecimal(amountToRefund)));
		amount.setCurrency(splitSalesOrder.getStoreCurrencyCode());
		payLoad.setTotalAmount(amount);
		return payLoad;
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
	
	private String baseUrl() {
		if (null != Constants.orderCredentials) {
			TamaraDetails tamara = Constants.orderCredentials.getTamara();
			return tamara != null ? tamara.getBaseUrl() : null;
		}
		return null;
	}
}
