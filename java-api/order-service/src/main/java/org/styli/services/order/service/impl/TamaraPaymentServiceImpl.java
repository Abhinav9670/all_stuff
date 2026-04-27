/**
 * 
 */
package org.styli.services.order.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.styli.services.order.helper.PaymentDtfHelper;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.PaymentDTO;
import org.styli.services.order.pojo.order.BNPLOrderUpdateResponse;
import org.styli.services.order.pojo.tamara.TamaraCaptures;
import org.styli.services.order.repository.SalesOrder.SalesOrderPaymentRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderPaymentRepository;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentConstants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author manineemahapatra
 *
 */

@Service
public class TamaraPaymentServiceImpl extends PaymentServiceImpl {

	private static final Log LOGGER = LogFactory.getLog(TamaraPaymentServiceImpl.class);
	
	private static final ObjectMapper mapper = new ObjectMapper();

	@Autowired
	private SalesOrderPaymentRepository paymentRepository;

	@Autowired
	private SplitSalesOrderPaymentRepository splitSalesOrderPaymentRepository;
	
	@Autowired
    PaymentDtfHelper paymentDtfHelper;
	
	@Override
	public BNPLOrderUpdateResponse updatePayment(PaymentDTO paymentDto, boolean isWebhook, String deviceId) {
		
			if (isApproved(paymentDto.getStatus()) || isAuthorised(paymentDto.getStatus())) {
				return processPaymentSuccess(paymentDto, isWebhook);
			}else if (isDeclined(paymentDto.getStatus()) || isExpired(paymentDto.getStatus())) {
				processPaymentFailure(paymentDto);
			}
		
		return null;
	}

	
	/**
	 * On Payment "Approved, Authorize TAMARA Payment and update the order status to processing.
	 * @param paymentDto
	 * @param isWebhook
	 * @return 
	 */
	public BNPLOrderUpdateResponse processPaymentSuccess(PaymentDTO paymentDto, boolean isWebhook) {
		BNPLOrderUpdateResponse response = new BNPLOrderUpdateResponse();
		response.setSuccess(false);
		SalesOrder salesOrder = salesOrderService.findSalesOrderByPaymentId(paymentDto.getId());
		if (Objects.isNull(salesOrder)) {
			ProxyOrder proxyOrder = proxyOrderRepository.findByPaymentId(paymentDto.getId());
			SalesOrder orderByIncId = salesOrderService.findSalesOrderByIncrementId(proxyOrder.getIncrementId());
			if (OrderConstants.FAILED_ORDER_STATUS.equals(proxyOrder.getStatus()) && isWebhook) {
				// Handle approved payment but order is already closed by replica
				LOGGER.info("Closing tamara approved payments :" + paymentDto.getId());
				try {
					SalesOrder order = mapper.readValue(proxyOrder.getSalesOrder(), SalesOrder.class);
					tamaraHelper.authorisedOrder(proxyOrder.getPaymentId());
					paymentUtility.initiateClose(order, order.getGrandTotal().toString(), proxyOrder.getPaymentMethod());
				} catch (JsonProcessingException e) {
					LOGGER.error("Error in closing tamara payment : " + proxyOrder.getPaymentId() + " Error : " + e);
				}
				response.setSuccess(true);
				return response;

			} else if (Objects.nonNull(orderByIncId)
					&& !paymentDto.getId().equals(orderByIncId.getSubSalesOrder().getPaymentId())) {
				// Order is already placed by another payment method.
				LOGGER.info("Tamara Payment authorized but order already processed with different payment: " + paymentDto.getId());
				onPaymentSuccessButStausFailed(proxyOrder);
				response.setSuccess(true);
				return response;
			} else {// Place the order internally
				salesOrder = placeOrderInternally(paymentDto, proxyOrder, isWebhook);
				if (Objects.isNull(salesOrder)) {
					response.setSuccess(false);
					return response;
				}
			}
		}
		if (!salesOrder.getStatus().equals(OrderConstants.PENDING_PAYMENT_ORDER_STATE)) {
			response.setErrorMessage("Order is already Processed! Status: " + salesOrder.getStatus());
			return response;
		}
		String paymentId = salesOrder.getSubSalesOrder().getPaymentId();
		String tamaraPaymentObject = tamaraHelper.authorisedOrder(paymentId);
		if (Objects.isNull(tamaraPaymentObject)) {
			LOGGER.error("Tamara PaymentID not authorized : " + paymentId);
			response.setErrorMessage("Tamara Payment is not authorised!");
			return response;
		}
		tamaraHelper.updateOrderId(salesOrder.getIncrementId(), paymentId);
		String paymentSuccessMsg = isWebhook ? OrderConstants.TAMARA_SUCCESS_MESSAGE
				: OrderConstants.TAMARA_QUERY_SUCCESS_MESSAGE;
		
		if (null != salesOrder.getSubSalesOrder().getRetryPayment()
				&& salesOrder.getSubSalesOrder().getRetryPayment() == 1) {
			paymentDtfHelper.setEstimatedDeliveryTimeForRetryPayment(salesOrder);
		}
		
		String modeOfPayment = PaymentConstants.TAMARA_INSTALMENTS_3;
		return onPaymentSuccess(paymentDto, salesOrder, paymentSuccessMsg, modeOfPayment);

	}
	
	private void onPaymentSuccessButStausFailed(ProxyOrder proxyOrder) {
		try {
			tamaraHelper.authorisedOrder(proxyOrder.getPaymentId());
			SalesOrder salesOrder = mapper.readValue(proxyOrder.getSalesOrder(), SalesOrder.class);
			tamaraHelper.cancelPayment(salesOrder, salesOrder.getGrandTotal().toString());
			proxyOrder.setStatus(OrderConstants.FAILED_ORDER_STATUS);
			proxyOrderRepository.saveAndFlush(proxyOrder);
		} catch (Exception e) {
			LOGGER.error("Error in Updating proxy order faile on authorized : " + proxyOrder.getIncrementId() + " Payment ID: " + proxyOrder.getPaymentId());
		}
		
	}

	/**
	 * On Payment Failure perform following actions.
	 * 1. If Inventory is not released, then set the inventory release flag to true.
	 * 2. Update the order incrementId in TAMARA payment order.
	 * 3. Release the Inventory.
	 * @param paymentDto
	 */
	public void processPaymentFailure(PaymentDTO paymentDto) {
		ProxyOrder proxyOrder = proxyOrderRepository.findByPaymentId(paymentDto.getId());
		if (Objects.isNull(proxyOrder) || proxyOrder.isInventoryReleased())
			return;
		try {
			SalesOrder salesOrder = mapper.readValue(proxyOrder.getSalesOrder(), SalesOrder.class);
			tamaraHelper.updateOrderId(salesOrder.getIncrementId(), paymentDto.getId());
			LOGGER.info("Orders are in pending_payment goinf to fail :"+salesOrder.getIncrementId());
			onProxyOrderPaymentFailure(salesOrder, proxyOrder);
		} catch (JsonProcessingException e) {
			orderHelperV2.updateProxyOrderInventoryRelease(proxyOrder.getId(), false);
			LOGGER.error("Error in Update Order Failure. ", e);
		}
	}

	@Override
	public void updatePaymentOnReplica(String paymentId, String deviceId) {
		PaymentDTO tabbyPayment = new PaymentDTO();
		tabbyPayment.setId(paymentId);
		tabbyPayment.setStatus("EXPIRED");
		LOGGER.info("Closing Tamara Payment Status On Replica " + paymentId);
		String tamaraPaymentStatus = tamaraHelper.retrievePayment(paymentId);
		if (Objects.nonNull(tamaraPaymentStatus)) {
			PaymentDTO paymetDTO = paymentUtility.buildPaymentDTOForTamara(tamaraPaymentStatus);
			ProxyOrder proxyOrder = proxyOrderRepository.findByPaymentId(paymentId);
			try {
				if(isApproved(paymetDTO.getStatus())) {
					tamaraHelper.authorisedOrder(paymentId);
					SalesOrder salesOrder = mapper.readValue(proxyOrder.getSalesOrder(), SalesOrder.class);
					tamaraHelper.cancelPayment(salesOrder, salesOrder.getGrandTotal().toString());
				}
			} catch (JsonProcessingException e) {
				LOGGER.error("Error in Closing Tamara Payment on Replica. Payment ID : " + paymentId + " Error : " + e);
			}
		}
		updatePayment(tabbyPayment, false, deviceId);
	}

	
	private boolean isApproved(String status) {
		return "approved".equalsIgnoreCase(status);
	}
	
	private boolean isDeclined(String status) {
		return "declined".equalsIgnoreCase(status);
	}
	
	private boolean isExpired(String status) {
		return "expired".equalsIgnoreCase(status);
	}
	
	private boolean isAuthorised(String status) {
		return "authorised".equalsIgnoreCase(status);
	}


	@Override
	public List<BNPLOrderUpdateResponse> getPaymentUpdates(String deviceId) {
		// Do Nothing
		return new ArrayList<>();
	}

	/**
	 * Update Order Payment Status for pending payment orders
	 * @return 
	 */
	@Override
	public BNPLOrderUpdateResponse updatePaymentStatus(ProxyOrder order, String deviceId) {
		String tamaraPayment = tamaraHelper.retrievePayment(order.getPaymentId());
		if (Objects.nonNull(tamaraPayment)) {
			PaymentDTO paymetDTO = paymentUtility.buildPaymentDTOForTamara(tamaraPayment);
			if (Objects.nonNull(paymetDTO))
				return updatePayment(paymetDTO, false, deviceId);
		}
		return null;
	}



	/**
	 * Capture the payment and store the capture_id for future refund process.
	 */
	@Override
	public boolean capturePayment(SalesOrder order) {
		String paymentId = order.getSubSalesOrder().getPaymentId();
		TamaraCaptures capturePayment = tamaraHelper.capturePayment(paymentId, order);
		Optional<SalesOrderPayment> orderPayment = order.getSalesOrderPayment().stream().findFirst();
		if(orderPayment.isPresent() && Objects.nonNull(capturePayment)) {
			SalesOrderPayment salesOrderPayment = paymentRepository.getOne(orderPayment.get().getEntityId());
			salesOrderPayment.setCcTransId(capturePayment.getCaptureId());
			paymentRepository.saveAndFlush(salesOrderPayment);
		}else {
			LOGGER.error("Tamara Payment is not captured for Order ID : " + order.getEntityId() + " Payment ID: " + paymentId);
			return false;
		}
		return true;
	}

	@Override
	public boolean capturePaymentV2(SplitSalesOrder splitSalesOrder) {
		String paymentId = splitSalesOrder.getSplitSubSalesOrder().getPaymentId();
		TamaraCaptures capturePayment = tamaraHelper.capturePaymentV2(paymentId, splitSalesOrder);
		Optional<SplitSalesOrderPayment> orderPayment = splitSalesOrder.getSplitSalesOrderPayments().stream().findFirst();
		if(orderPayment.isPresent() && Objects.nonNull(capturePayment)) {
			SplitSalesOrderPayment splitSalesOrderPayment = splitSalesOrderPaymentRepository.getOne(orderPayment.get().getEntityId());
			splitSalesOrderPayment.setCcTransId(capturePayment.getCaptureId());
			splitSalesOrderPaymentRepository.saveAndFlush(splitSalesOrderPayment);
		}else {
			LOGGER.error("Tamara Payment is not captured for split Order ID : " + splitSalesOrder.getEntityId() + " Payment ID: " + paymentId);
			return false;
		}
		return true;
	}
}
