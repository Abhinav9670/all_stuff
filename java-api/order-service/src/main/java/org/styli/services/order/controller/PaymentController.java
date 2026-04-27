/**
 * 
 */
package org.styli.services.order.controller;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.styli.services.order.config.aop.ConfigurableDatasource;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.pojo.OrderSms;
import org.styli.services.order.pojo.PaymentDTO;
import org.styli.services.order.pojo.cashfree.CashgramWebhookDTO;
import org.styli.services.order.pojo.order.BNPLOrderUpdateResponse;
import org.styli.services.order.pojo.tabby.TabbyRefundDTO;
import org.styli.services.order.service.ConfigService;
import org.styli.services.order.service.PaymentService;
import org.styli.services.order.service.impl.CashfreePaymentServiceImpl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.PaymentUtility;

import io.swagger.annotations.Api;

/**
 * @author maninee mahapatra
 * @Date 
 */



@RestController
@RequestMapping("/rest/order")
@Api(value = "/rest/order/tabby/", produces = "application/json")
public class PaymentController {
	
	private static final Log LOGGER = LogFactory.getLog(PaymentController.class);

	@Autowired
	@Qualifier("tabbyPaymentServiceImpl")
	private PaymentService tabbyPaymentService;
	
	@Autowired
	@Qualifier("tamaraPaymentServiceImpl")
	private PaymentService tamaraPaymentService;
	
	@Autowired
	@Qualifier("cashfreePaymentServiceImpl")
	private PaymentService cfPaymentService;
	
	@Autowired
	ConfigService configService;
	
	@Autowired
	PaymentUtility paymentUtility;
	
	/**
	 * 
	 * @param tabbyPayment
	 * @return
	 */
	@PostMapping("/tabby/webhook")
	@ConfigurableDatasource
	public ResponseEntity<Boolean> getWebhook(@RequestBody String tabbyPayment, @RequestHeader(value = "device-id", required = false) String deviceId) {
		LOGGER.info("Tabby Webhook : " + tabbyPayment);
		PaymentDTO paymetDTO = paymentUtility.buildPaymentDTOForTabby(tabbyPayment);
		BNPLOrderUpdateResponse response = tabbyPaymentService.updatePayment(paymetDTO, true, deviceId);
		executePaymentSuccess(response);
		return ResponseEntity.ok(true);
	}

	/**
	 * 
	 * @return
	 */
	@GetMapping("/tabby/update/status")
	@ConfigurableDatasource
	public ResponseEntity<String> getTabbyPaymentStatus(
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken, @RequestHeader(value = "device-id", required = false) String deviceId) {
		List<BNPLOrderUpdateResponse> response = null;
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("You're not authenticated to make tabby request.");
			}
			response = tabbyPaymentService.getPaymentUpdates(deviceId);
		} else {
			response = tabbyPaymentService.getPaymentUpdates(deviceId);
		}
		if (Objects.nonNull(response) && !response.isEmpty()) {
			response.forEach(this::executePaymentSuccess);
		}
		return ResponseEntity.ok(Objects.nonNull(response) ? "Order Status Update Success!" : "Order Status Update failed!");
	}
	
	
	@PostMapping("/refund")
	@ConfigurableDatasource
	public ResponseEntity<String> refundTabbyPayment(@RequestBody TabbyRefundDTO tabbyPayment, 
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
		String response;
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("You're not authenticated to make this request.");
			}
			response = tabbyPaymentService.refundPayment(tabbyPayment.getOrderId());
		} else {
			response = tabbyPaymentService.refundPayment(tabbyPayment.getOrderId());
		}
		return ResponseEntity.ok(response);
	}
	
	@PostMapping("/tamara/webhook")
	@ConfigurableDatasource
	public ResponseEntity<Boolean> tamaraWebhook(@RequestBody String tamaraPayment, @RequestHeader(value = "device-id", required = false) String deviceId) {
		LOGGER.info("Tamara Webhook : " + tamaraPayment);
		try {
			PaymentDTO paymetDTO = paymentUtility.buildPaymentDTOForTamaraWebhook(tamaraPayment);
			BNPLOrderUpdateResponse response = tamaraPaymentService.updatePayment(paymetDTO, true, deviceId);
			executePaymentSuccess(response);
			return ResponseEntity.ok(true);
		} catch (Exception e) {
			LOGGER.error("Error In Processing Tamra webhook. " + e);
			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(false);
		}
	}

	private void executePaymentSuccess(BNPLOrderUpdateResponse response) {
		try {
			if (Objects.nonNull(response) && response.isPaymentSuccess()) {
				LOGGER.info("Sending Order to Kafka for SMS trigger : " + response.getOrderEntityId());
				OrderSms ordersms = new OrderSms();
				ordersms.setOrderid(response.getOrderEntityId().toString());
				paymentUtility.publishToKafka(ordersms);
				paymentUtility.publishToSplitPubSub(response.getOrderEntityId());
                paymentUtility.publishToSplitPubSubOTS(response.getOrderEntityId(),null,null);
			}
		} catch (Exception e) {
			LOGGER.error("Error in publishing message to kafka. " + e);
		}
	}
	
	/**
	 * Update Proxy Orders which are in Pending Payment State
	 * @param authorizationToken
	 * @return
	 */
	@GetMapping("/bnpl/update/status")
	@ConfigurableDatasource
	public ResponseEntity<String> updateInventoryForExpiredOrder(
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken, @RequestHeader(value = "device-id", required = false) String deviceId) {
		List<BNPLOrderUpdateResponse> response;
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("You're not authenticated to make this request.");
			}
			response = updateProxyOrderPayemnt(deviceId);
		} else {
			response = updateProxyOrderPayemnt(deviceId);
		}
		if (!response.isEmpty()) {
			response.forEach(this::executePaymentSuccess);
		}
		return ResponseEntity.ok("Orders Updated!");
	}
	
	private List<BNPLOrderUpdateResponse> updateProxyOrderPayemnt(String deviceId) {
		List<BNPLOrderUpdateResponse> responses = new ArrayList<>();
		List<ProxyOrder> proxyOrders = paymentUtility.findPendingOrders();
		if (proxyOrders.isEmpty()) {
			LOGGER.info("There is no proxy orders to release inventory");
			return responses;
		}
		List<String> orders = proxyOrders.stream().map(ProxyOrder::getPaymentId).collect(Collectors.toList());
		LOGGER.info("Orders are in block inventory state and needs to be released :" + orders);
		for (ProxyOrder proxyOrder : proxyOrders) {
			BNPLOrderUpdateResponse resp = paymentUtility.updatePaymentStatus(proxyOrder, deviceId);
			responses.add(resp);
		}
		return responses;
	}
	
	@PostMapping("/cf/webhook")
	@ConfigurableDatasource
	public ResponseEntity<Boolean> cashfreeWebhook(@RequestBody String cfPayment, @RequestHeader(value = "device-id", required = false) String deviceId) {
		LOGGER.info("Cashfree Webhook : " + cfPayment);
		try {
			PaymentDTO paymetDTO = paymentUtility.buildPaymentDTOForCashfree(cfPayment);
			BNPLOrderUpdateResponse response = cfPaymentService.updatePayment(paymetDTO, true, deviceId);
			executePaymentSuccess(response);
			return ResponseEntity.ok(true);
		} catch (Exception e) {
			LOGGER.error("Error In Processing Cashfree webhook. " + e);
			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(false);
		}
	}
	
	@GetMapping("/cf/update/status")
	public ResponseEntity<String> getCfPaymentStatus(
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken, @RequestHeader(value = "device-id", required = false) String deviceId) {
		List<BNPLOrderUpdateResponse> response = null;
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("You're not authenticated to make cashfree request.");
			}
			response = cfPaymentService.getPaymentUpdates(deviceId);
		} else {
			response = cfPaymentService.getPaymentUpdates(deviceId);
		}
		if (Objects.nonNull(response) && !response.isEmpty()) {
			response.forEach(this::executePaymentSuccess);
		}
		return ResponseEntity.ok(Objects.nonNull(response) ? "Order Status Update Success!" : "Order Status Update failed!");
	}
	
	@PostMapping("/cf/cashgram/webhook")
	public ResponseEntity<String> cashgramRefundWebhook(@ModelAttribute CashgramWebhookDTO payload){
		LOGGER.info("Cashgram refund webhook Payload : " + payload);
		CashfreePaymentServiceImpl cfService = (CashfreePaymentServiceImpl)cfPaymentService;
		cfService.cashgramWebhook(payload);
		return ResponseEntity.ok("Cashgram Status Update Success!");
	}
	

}
