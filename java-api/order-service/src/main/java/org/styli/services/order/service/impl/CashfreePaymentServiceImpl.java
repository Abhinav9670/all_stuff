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
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.rma.AmastyRmaRequestItem;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.pojo.PaymentDTO;
import org.styli.services.order.pojo.RefundPaymentRespone;
import org.styli.services.order.pojo.cashfree.CashgramDataDTO;
import org.styli.services.order.pojo.cashfree.CashgramWebhookDTO;
import org.styli.services.order.pojo.order.BNPLOrderUpdateResponse;
import org.styli.services.order.pojo.order.PaymentReturnAdditioanls;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.utility.OrderConstants;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * @author manineemahapatra
 *
 */

@Service
public class CashfreePaymentServiceImpl extends PaymentServiceImpl {

	private static final Log LOGGER = LogFactory.getLog(CashfreePaymentServiceImpl.class);
	private static final String CF_WEBHOOK_RESPONSE_MESSAGE = "Cashfree Webhook Payment Failed ";
	private static final String CF_SCHEDULER_RESPONSE_MESSAGE = "Cashfree Scheduler update Payment Failed";

	@Autowired
	private AmastyRmaRequestRepository amastyRmaRequestRepository;
	
	@Override
	public BNPLOrderUpdateResponse updatePayment(PaymentDTO cfPayment, boolean isWebhook, String deviceId) {
		return processUpdatePayment(cfPayment, isWebhook, deviceId);
	}

	/**
	 * 
	 * @param cfPayment
	 * @param isWebhook
	 * @return
	 */
	private BNPLOrderUpdateResponse processUpdatePayment(PaymentDTO cfPayment, boolean isWebhook, String deviceId) {
		SalesOrder salesOrder = salesOrderService.findSalesOrderByIncrementId(cfPayment.getOrderId());
		if (Objects.isNull(salesOrder)) {
			LOGGER.info("Increment ID Not found : " + cfPayment.getOrderId());
			return null;
		}
		salesOrder.getSubSalesOrder().setPaymentId(cfPayment.getId());
		salesOrder.setSubSalesOrder(salesOrder.getSubSalesOrder());
		try {
			LOGGER.info("Cashfree Webhook Request : " + objectMapper.writeValueAsString(cfPayment));
		} catch (JsonProcessingException e) {
			LOGGER.error("parsing error in cashfree webhook request" + e);
		}
		if (isSuccess(cfPayment.getStatus())) {
			String paymentSuccessMsg = isWebhook ? OrderConstants.CF_SUCCESS_MESSAGE
					: OrderConstants.CF_QUERY_SUCCESS_MESSAGE;
			return processPaymentSuccess(cfPayment, salesOrder, paymentSuccessMsg);
		} else if (isFailed(cfPayment.getStatus())) {
			if (isWebhook) {
				String cfPaymentStatus = cashfreeHelper.retrievePayment(salesOrder.getIncrementId());
				if(Objects.isNull(cfPaymentStatus))
					return null;

				PaymentDTO paymetDTO = paymentUtility.buildPaymentDTOForCashfreeRetrievePayment(cfPaymentStatus);
				if("ACTIVE".equalsIgnoreCase(paymetDTO.getStatus()))
					return null;
			}
			processPaymentFailure(cfPayment, salesOrder, isWebhook, deviceId);
		}else if(isExpired(cfPayment.getStatus())){
			processPaymentFailure(cfPayment, salesOrder, isWebhook, deviceId);
		}else {
			return null;
		}
    return null;
		
	}

	public BNPLOrderUpdateResponse processPaymentSuccess(PaymentDTO cfPayment, SalesOrder salesOrder,
			String paymentSuccessMsg) {
		if (!salesOrder.getStatus().equals(OrderConstants.PENDING_PAYMENT_ORDER_STATE)) {
			return null;
		}
		return onPaymentSuccess(cfPayment, salesOrder, paymentSuccessMsg, null);
	}

	public void processPaymentFailure(PaymentDTO cfPayment, SalesOrder salesOrder, boolean isWebhook, String deviceId) {
		try {
			if (null != salesOrder
					&& salesOrder.getStatus().equalsIgnoreCase(OrderConstants.PENDING_PAYMENT_ORDER_STATUS)) {
				String paymentFailedMsg = isWebhook ? CF_WEBHOOK_RESPONSE_MESSAGE : CF_SCHEDULER_RESPONSE_MESSAGE;
				onPaymentFailure(cfPayment, salesOrder, paymentFailedMsg, deviceId);
			}
		} catch (Exception e) {
			updateOrderStatus(salesOrder, OrderConstants.PENDING_PAYMENT_ORDER_STATUS);
			LOGGER.error("Error in CF Payment Failure Status Update. ", e);
		}
	}

	private boolean isSuccess(String status) {
		return "SUCCESS".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(status);
	}

	private boolean isFailed(String status) {
		return "FAILED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status);
	}

	private boolean isExpired(String status) {
		return "USER_DROPPED".equalsIgnoreCase(status);
	}

	/**
	 * On Payment "Authorize" update order incrementID in TABBY and update the order
	 * status to processing.
	 * 
	 * @param paymentDto
	 * @param isWebhook
	 * @return
	 */
	public BNPLOrderUpdateResponse processPaymentSuccess(PaymentDTO paymentDto, boolean isWebhook,
			ProxyOrder proxyOrder) {
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
		String paymentSuccessMsg = OrderConstants.CF_SUCCESS_MESSAGE;
		return onPaymentSuccess(paymentDto, salesOrder, paymentSuccessMsg, null);
	}

	@Override
	public void updatePaymentOnReplica(String paymentId, String deviceId) {
		// Nothing to do
	}

	@Override
	public BNPLOrderUpdateResponse updatePaymentStatus(ProxyOrder order, String deviceId) {
		return null;
	}

	@Override
	public boolean capturePayment(SalesOrder order) {
		return false;
	}

	@Override
	public boolean capturePaymentV2(SplitSalesOrder splitSalesOrder) {
		return false;
	}

	@Override
	public List<BNPLOrderUpdateResponse> getPaymentUpdates(String deviceId) {
		List<SalesOrder> salesOrder = salesOrderService.findSalesOrdeForCfPayment();
		if (salesOrder.isEmpty()) {
			LOGGER.info("There is no more orders in pending_payment state");
			return new ArrayList<>();
		}
		List<Integer> orders = salesOrder.stream().map(SalesOrder::getEntityId).collect(Collectors.toList());
		LOGGER.info("Orders are in pending_payment state and needs to be updated :" + orders);
		List<BNPLOrderUpdateResponse> responses = new ArrayList<>();
		for (SalesOrder salesOrder2 : salesOrder) {
			try {
				String cfPayment = cashfreeHelper.retrievePayment(salesOrder2.getIncrementId());
				if (Objects.nonNull(cfPayment)) {
					PaymentDTO paymetDTO = paymentUtility.buildPaymentDTOForCashfreeRetrievePayment(cfPayment);
					BNPLOrderUpdateResponse response = updatePayment(paymetDTO, false, deviceId);
					responses.add(response);
				}
			} catch (Exception e) {
				LOGGER.error("Error In Updating Cashfree payment Status. Error : " + e);
			}
		}
		return responses;
	}
	
	/**
	 * Generate Cashgram refund link
	 * @param order
	 * @param returnAmount
	 * @return
	 */
	public RefundPaymentRespone initiateCashgramRefund(SalesOrder order, PaymentReturnAdditioanls addtionals) {
		String returnAmount = addtionals.getReturnAmount();
		AmastyRmaRequest rmaRequest = addtionals.getRmaRequest();
		try {
			RefundPaymentRespone refund = new RefundPaymentRespone();
			CashgramDataDTO response = cashfreeHelper.createCashgram(order, addtionals);
			if(Objects.nonNull(response)) {
				updateReturnUrl(rmaRequest, response);
				refund.setRefundUrl(response.getCashgramLink());
			}
			
			refund.setSendSms(true);
			refund.setStatusMsg("Refund link sent to customer.");
			refund.setRefundAmount(returnAmount);
			refund.setStatus(true);
			refund.setStatusCode("200");
			return refund;
		} catch (Exception e) {
			LOGGER.error("Error In Generating Refund link. Error : " + e);
		}
		return null;
	}
	
	/**
	 * Update Return Cashgram return URL.
	 * @param rmaRequest
	 * @param returnUrl
	 */
	private void updateReturnUrl(AmastyRmaRequest rmaRequest, CashgramDataDTO response) {
		try {
			AmastyRmaRequest rmaReq = amastyRmaRequestRepository.findByRmaIncId(rmaRequest.getRmaIncId());
			rmaReq.setUrlHash(response.getCashgramLink());
			rmaReq.setRmaPaymentExpireOn(response.getExpiry());
			amastyRmaRequestRepository.save(rmaReq);
		} catch (Exception e) {
			LOGGER.error("Error in updating cashgram return url " + e);
		}
	}

	/**
	 * Process Cashgram webhook & update the status. 
	 * @param payload
	 */
	@Transactional
	public void cashgramWebhook(CashgramWebhookDTO payload) {
		try {
			if (cashfreeHelper.validateSignature(payload)) {
				LOGGER.info("Cashgram Singature Validation success");
				String rmaIncId = payload.getCashgramId();
				AmastyRmaRequest amastyRmaRequest = amastyRmaRequestRepository.findByRmaIncId(rmaIncId);
				switch (payload.getEvent()) {
				case "CASHGRAM_REDEEMED":
					updateRefundStatus(amastyRmaRequest, 43);
					break;
				case "CASHGRAM_EXPIRED":
					updateRefundStatus(amastyRmaRequest, 41);
					break;
				case "CASHGRAM_TRANSFER_REVERSAL":
					updateRefundStatus(amastyRmaRequest, 42);
					break;
				default:
					updateRefundStatus(amastyRmaRequest, 40);
					break;
				}
			}else {
				LOGGER.info("Cashgram Webhook Sinature didn't match. " + payload);
			}
		} catch (Exception e) {
			LOGGER.error("Error In processing cashgram webhook. Error : " + e);
		}
	}

	private void updateRefundStatus(AmastyRmaRequest amastyRmaRequest, int status) {
		try {
			for (AmastyRmaRequestItem rmaItem : amastyRmaRequest.getAmastyRmaRequestItems()) {
				rmaItem.setItemStatus(status);
			}
			amastyRmaRequest.setStatus(status);
		} catch (Exception e) {
			LOGGER.error("Error In Updating cashgram webhook Status. RMA ID : " + amastyRmaRequest.getRmaIncId()
					+ ". Error : " + e);
		}
	}

	

}
