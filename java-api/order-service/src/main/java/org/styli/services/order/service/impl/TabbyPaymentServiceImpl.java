package org.styli.services.order.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.styli.services.order.helper.PaymentDtfHelper;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderPayment;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.pojo.PaymentDTO;
import org.styli.services.order.pojo.order.BNPLOrderUpdateResponse;
import org.styli.services.order.pojo.tabby.TabbyPayment;
import org.styli.services.order.utility.OrderConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.styli.services.order.utility.PaymentConstants;

/**
 * @author manineemahapatra
 *
 */

@Service
public class TabbyPaymentServiceImpl extends PaymentServiceImpl {

	private static final Log LOGGER = LogFactory.getLog(TabbyPaymentServiceImpl.class);
	private static final String TABBY_RESPONSE_MESSAGE = "Tabby Query Payment Failed ";
	private static final String TABBY_WEBHOOK_RESPONSE_MESSAGE = "Tabby Webhook Payment Failed ";
	private static final ObjectMapper mapper = new ObjectMapper();
	
	@Autowired
    PaymentDtfHelper paymentDtfHelper;

	@Override
	public BNPLOrderUpdateResponse updatePayment(PaymentDTO tabbyPayment, boolean isWebhook, String deviceId) {
		ProxyOrder proxyOrder = proxyOrderRepository.findByPaymentId(tabbyPayment.getId());
		if (Objects.isNull(proxyOrder)) {
			return processUpdatePayment(tabbyPayment, isWebhook, true, deviceId);
		} else {
			return processUpdatePaymentForProxy(tabbyPayment, isWebhook, proxyOrder);
		}
	}

	/**
	 * 
	 * @param tabbyPayment
	 * @param isWebhook
	 * @return
	 */
	private BNPLOrderUpdateResponse processUpdatePayment(PaymentDTO tabbyPayment, boolean isWebhook, boolean isNotProxy, String deviceId) {
		SalesOrder salesOrder = salesOrderService.findSalesOrderByPaymentId(tabbyPayment.getId());
		if (Objects.isNull(salesOrder)) {
			LOGGER.info("updatePayment : Tabby Payment - sales order not found for paymentId=" + tabbyPayment.getId());
			return null;
		}
		SalesOrderPayment salesOrderPayment = (salesOrder.getSalesOrderPayment() != null)
				? salesOrder.getSalesOrderPayment().stream().findFirst().orElse(null)
				: null;
		if (Objects.isNull(salesOrderPayment)) {
			LOGGER.info("updatePayment : Tabby Payment - order payment not found for order=" + salesOrder.getIncrementId());
			return null;
		}
		try {
			LOGGER.info("updatePayment : tabby Response:" + objectMapper.writeValueAsString(tabbyPayment));
		} catch (JsonProcessingException e) {
			LOGGER.info("updatePayment : Tabby Payment processing error while logging");
		}
		if (isAuthorized(tabbyPayment.getStatus())
				&& salesOrder.getStatus().equals(OrderConstants.FAILED_ORDER_STATUS)) {
			onPaymentSuccessButStausFailed(tabbyPayment);
		} else if (isAuthorized(tabbyPayment.getStatus()) && OrderConstants.checkBNPLPaymentMethods(salesOrderPayment.getMethod())) {
			return processPaymentSuccess(tabbyPayment, salesOrder, isWebhook, isNotProxy);
		} else if (isRejected(tabbyPayment.getStatus()) || isExpired(tabbyPayment.getStatus())) {
			processPaymentFailure(tabbyPayment, salesOrder, isWebhook, deviceId);
		}
		return null;
	}

	/**
	 * Process Webhook for Proxy orderss
	 * 
	 * @param paymentDto
	 * @param isWebhook
	 * @return 
	 */
	public BNPLOrderUpdateResponse processUpdatePaymentForProxy(PaymentDTO paymentDto, boolean isWebhook, ProxyOrder proxyOrder) {

		SalesOrder salesOrder = salesOrderService.findSalesOrderByIncrementId(proxyOrder.getIncrementId());
		String paymentId = "";
		if(Objects.nonNull(salesOrder))
			paymentId = salesOrder.getSubSalesOrder().getPaymentId();
		
		if (isAuthorized(paymentDto.getStatus()) && null != proxyOrder.getStatus() 
				&& proxyOrder.getStatus().equals(OrderConstants.FAILED_ORDER_STATUS)) {
			
			LOGGER.info("updatePayment : proxy order status authorized but order status failed");
			onPaymentSuccessButStausFailed(paymentDto);

		} else if (isAuthorized(paymentDto.getStatus()) && Objects.nonNull(salesOrder)
				&& !paymentDto.getId().equals(paymentId)) {
			
			LOGGER.info("updatePayment : Tabby payment authorized but order already processed with different payment: " + paymentId);
			onPaymentSuccessButStausFailed(paymentDto, proxyOrder);
			
		} else if (isAuthorized(paymentDto.getStatus())) {
			
			LOGGER.info("updatePayment : proxy order status authorized !!");
			return processPaymentSuccess(paymentDto, isWebhook, proxyOrder);
			
		} else if (isRejected(paymentDto.getStatus()) || isExpired(paymentDto.getStatus())) {
			
			LOGGER.info("updatePayment : proxy order status rejected or expired !!");
			processPaymentProxyFailure(paymentDto);
		}
		return null;
	}

	public void onPaymentSuccessButStausFailed(PaymentDTO tabbyPayment) {
		LOGGER.info("inside tabby close call : " + tabbyPayment.getId());
		TabbyPayment tabbyPaymentClose = tabbyHelper.closePayment(tabbyPayment.getId());
		try {
			LOGGER.info("tabby Response:" + objectMapper.writeValueAsString(tabbyPaymentClose));
		} catch (JsonProcessingException e) {
			LOGGER.error("exception occoured during tabby close call:"+e.getMessage());
		}
	}

	public void onPaymentSuccessButStausFailed(PaymentDTO tabbyPayment, ProxyOrder proxyOrder) {
		try {
			onPaymentSuccessButStausFailed(tabbyPayment);
			proxyOrder.setStatus(OrderConstants.FAILED_ORDER_STATUS);
			proxyOrderRepository.saveAndFlush(proxyOrder);
		} catch (Exception e) {
			LOGGER.error("exception occoured during tabby close call on payment success :" + e.getMessage());
		}
	}
	
	public BNPLOrderUpdateResponse processPaymentSuccess(PaymentDTO tabbyPayment, SalesOrder salesOrder, boolean isWebhook,
			boolean isNotProxy) {
		if (!salesOrder.getStatus().equals(OrderConstants.PENDING_PAYMENT_ORDER_STATE)) {
			return null;
		}
		boolean updateOrderIdInTabby = tabbyHelper.updateOrderId(salesOrder.getIncrementId(), tabbyPayment.getId());
		if (updateOrderIdInTabby) {
			boolean capturePaymentInTabby = tabbyHelper.capturePayment(tabbyPayment.getId(), salesOrder, isNotProxy);
			if (capturePaymentInTabby) {
				String paymentSuccessMsg = isWebhook ? OrderConstants.TABBY_SUCCESS_MESSAGE
						: OrderConstants.TABBY_QUERY_SUCCESS_MESSAGE;
				if (null != salesOrder.getSubSalesOrder().getRetryPayment()
						&& salesOrder.getSubSalesOrder().getRetryPayment() == 1) {
					paymentDtfHelper.setEstimatedDeliveryTimeForRetryPayment(salesOrder);
				}
				String modeOfPayment = PaymentConstants.TABBY_INSTALMENTS;
				return onPaymentSuccess(tabbyPayment, salesOrder, paymentSuccessMsg, modeOfPayment);
		

			}
		}
		return null;
	}

	public void processPaymentFailure(PaymentDTO tabbyPayment, SalesOrder salesOrder, boolean isWebhook, String deviceId) {
		try {
			if (null != salesOrder
					&& salesOrder.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)) {
				boolean updateOrderId = tabbyHelper.updateOrderId(salesOrder.getIncrementId(), tabbyPayment.getId());
				if (!updateOrderId) {
					LOGGER.info("Order incrementId is not updated in Tabby. " + tabbyPayment.getId());
					return;
				}
				String paymentFailedMsg = isWebhook ? TABBY_WEBHOOK_RESPONSE_MESSAGE : TABBY_RESPONSE_MESSAGE;
				onPaymentFailure(tabbyPayment, salesOrder, paymentFailedMsg, deviceId);
			}
		} catch (Exception e) {
			updateOrderStatus(salesOrder, OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
			LOGGER.error("Error in Tabby Payment Failure Status Update. ", e);
		}
	}

	@Override
	@Transactional
	public List<BNPLOrderUpdateResponse> getPaymentUpdates(String deviceId) {
		List<SalesOrder> salesOrder = salesOrderService.findSalesOrdeForTabbyPayment();
		if (salesOrder.isEmpty()) {
			LOGGER.info("There si no more orders in pending_payment state");
			return new ArrayList<>();
		}
		List<String> orders = salesOrder.stream().map(SalesOrder::getIncrementId).collect(Collectors.toList());
		LOGGER.info("Orders are in pending_payment state and needs to be updated all :" + orders);
		List<String> proxyOrders = proxyOrderRepository.findAllByIncrementId(orders);
		List<SalesOrder> notProxyOrders = salesOrder.stream().filter(ord -> !proxyOrders.contains(ord.getIncrementId())).collect(Collectors.toList());
		List<String>  notProxyOrderIds = notProxyOrders.stream().map(SalesOrder::getIncrementId).collect(Collectors.toList());
		LOGGER.info("Orders are in pending_payment state and needs to be updated And not proxy Order :" + notProxyOrderIds);
		List<BNPLOrderUpdateResponse> responses = new ArrayList<>();
		for (SalesOrder salesOrder2 : notProxyOrders) {
			try {
				String tabbyPayment = tabbyHelper.retrievePayment(salesOrder2.getSubSalesOrder().getPaymentId(), salesOrder2.getStoreId());
				if (Objects.nonNull(tabbyPayment)) {
					PaymentDTO paymetDTO = paymentUtility.buildPaymentDTOForTabby(tabbyPayment);
					BNPLOrderUpdateResponse response = updatePayment(paymetDTO, false, deviceId);
					responses.add(response);
				}
			} catch (Exception e) {
				LOGGER.error("Error In Updating Tabby payment Status. Error : " + e);
			}
		}
		return responses;
	}

	@Override
	public void updatePaymentOnReplica(String paymentId, String deviceId) {
		PaymentDTO tabbyPayment = new PaymentDTO();
		tabbyPayment.setId(paymentId);
		tabbyPayment.setStatus("EXPIRED");
		
		LOGGER.info("Closing Tabby Payment Status On Replica " + paymentId);
		String tabbyPaymentRes = tabbyHelper.retrievePayment(paymentId);
		if (Objects.nonNull(tabbyPaymentRes)) {
			PaymentDTO paymetDTO = paymentUtility.buildPaymentDTOForTabby(tabbyPaymentRes);
			if(isAuthorized(paymetDTO.getStatus())) tabbyHelper.closePayment(paymentId);
		}
		updatePayment(tabbyPayment, false, deviceId);
	}

	private boolean isAuthorized(String status) {
		return "AUTHORIZED".equalsIgnoreCase(status);
	}

	private boolean isRejected(String status) {
		return "REJECTED".equalsIgnoreCase(status);
	}

	private boolean isExpired(String status) {
		return "EXPIRED".equalsIgnoreCase(status);
	}

	/**
	 * On Payment Failure perform following actions. 1. If Inventory is not
	 * released, then set the inventory release flag to true. 2. Update the order
	 * incrementId in TABBY payment order. 3. Release the Inventory.
	 * 
	 * @param paymentDto
	 */
	public void processPaymentFailure(PaymentDTO paymentDto) {
				
		ProxyOrder proxyOrder = proxyOrderRepository.findByPaymentId(paymentDto.getId());
		if (Objects.isNull(proxyOrder) || proxyOrder.isInventoryReleased())
			return;
		try {
			SalesOrder salesOrder = mapper.readValue(proxyOrder.getSalesOrder(), SalesOrder.class);
			tabbyHelper.updateOrderId(salesOrder.getIncrementId(), paymentDto.getId());
			LOGGER.info("Orders are in pending_payment going to fail::"+salesOrder.getIncrementId());	
			onProxyOrderPaymentFailure(salesOrder, proxyOrder);
		} catch (JsonProcessingException e) {
			orderHelperV2.updateProxyOrderInventoryRelease(proxyOrder.getId(), false);
			LOGGER.error("Error in Update in Order Failure. ", e);
		}
	}
	
	/**
	 * On Payment Failure perform following actions. 1. If Inventory is not
	 * released, then set the inventory release flag to true. 2. Update the order
	 * incrementId in TABBY payment order. 3. Release the Inventory.
	 * 
	 * @param paymentDto
	 */
	public void processPaymentProxyFailure(PaymentDTO paymentDto) {
				
		ProxyOrder proxyOrder = proxyOrderRepository.findByPaymentId(paymentDto.getId());
		if (Objects.isNull(proxyOrder) || proxyOrder.isInventoryReleased())
			return;
		try {
			SalesOrder salesOrder = mapper.readValue(proxyOrder.getSalesOrder(), SalesOrder.class);
			tabbyHelper.updateOrderId(salesOrder.getIncrementId(), paymentDto.getId());
			LOGGER.info("Orders are in pending_payment going to fail :" + salesOrder.getIncrementId());	
			onProxyOrderPaymentFailure(salesOrder, proxyOrder);
		} catch (JsonProcessingException e) {
			orderHelperV2.updateProxyOrderInventoryRelease(proxyOrder.getId(), false);
			LOGGER.error("Error in Update Order Failure. ", e);
		}
	}

	/**
	 * On Payment "Authorize" update order incrementID in TABBY and update the order
	 * status to processing.
	 * 
	 * @param paymentDto
	 * @param isWebhook
	 * @return 
	 */
	public BNPLOrderUpdateResponse processPaymentSuccess(PaymentDTO paymentDto, boolean isWebhook, ProxyOrder proxyOrder) {
		BNPLOrderUpdateResponse response = new BNPLOrderUpdateResponse();
		SalesOrder salesOrder = salesOrderService.findSalesOrderByPaymentId(paymentDto.getId());
		if (Objects.isNull(salesOrder)) {
			salesOrder = placeOrderInternally(paymentDto, proxyOrder, isWebhook);
			if (Objects.isNull(salesOrder))
				return null;
		}
		if (!salesOrder.getStatus().equals(OrderConstants.PENDING_PAYMENT_ORDER_STATE)) {
			response.setSuccess(false);
			return response;
		}
		tabbyHelper.updateOrderId(salesOrder.getIncrementId(), paymentDto.getId());
		String paymentSuccessMsg = isWebhook ? OrderConstants.TABBY_SUCCESS_MESSAGE
				: OrderConstants.TABBY_QUERY_SUCCESS_MESSAGE;
		if (null != salesOrder.getSubSalesOrder().getRetryPayment() 
				&& salesOrder.getSubSalesOrder().getRetryPayment() == 1) {
			paymentDtfHelper.setEstimatedDeliveryTimeForRetryPayment(salesOrder);
		}
		
		String modeOfPayment = PaymentConstants.TABBY_INSTALMENTS;
		return onPaymentSuccess(paymentDto, salesOrder, paymentSuccessMsg, modeOfPayment);
	}

	/**
	 * Update Order Payment Status for pending payment orders
	 * @return 
	 */
	@Override
	public BNPLOrderUpdateResponse updatePaymentStatus(ProxyOrder order, String deviceId) {
		String payment = tabbyHelper.retrievePayment(order.getPaymentId(), order.getStoreId());
		if (Objects.nonNull(payment)) {
			PaymentDTO paymetDTO = paymentUtility.buildPaymentDTOForTabby(payment);
			if (Objects.nonNull(paymetDTO))
				return updatePayment(paymetDTO, false, deviceId);
		}
		return null;
	}

	@Override
	public boolean capturePayment(SalesOrder order) {
		String paymentId = order.getSubSalesOrder().getPaymentId();
		return tabbyHelper.capturePayment(paymentId, order, false);
	}

	@Override
	public boolean capturePaymentV2(SplitSalesOrder splitSalesOrder) {
		String paymentId = splitSalesOrder.getSplitSubSalesOrder().getPaymentId();
		return tabbyHelper.capturePaymentV2(paymentId, splitSalesOrder, false);
	}
}