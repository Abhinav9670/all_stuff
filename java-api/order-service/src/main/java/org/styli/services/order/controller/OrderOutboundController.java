package org.styli.services.order.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.styli.services.order.db.product.exception.ForbiddenException;
import org.styli.services.order.pojo.GenericApiResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderDetailRequest;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderDetailResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderListRequest;
import org.styli.services.order.pojo.whatsapp.bot.MobileOrderListResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileReturnDetailResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileShipmentListResponse;
import org.styli.services.order.pojo.whatsapp.bot.MobileShipmentDetailResponse;
import org.styli.services.order.pojo.response.Order.CustomerOrdersResponseDTO;
import org.styli.services.order.service.*;
import org.styli.services.order.service.impl.PromoService;
import org.styli.services.order.service.InvoiceSharingService;
import org.styli.services.order.utility.Constants;

import javax.validation.Valid;
import java.util.Map;

@RestController

@RequestMapping("/rest/order/")
@Api(value = "/rest/order/auth/", produces = "application/json")
@JsonIgnoreProperties(ignoreUnknown = true)

public class OrderOutboundController {

	@Autowired
	SalesOrderService salesOrderService;

	@Autowired
	WhatsappBotService whatsappBotService;

	@Autowired
	SalesOrderRetryService salesOrderRetryService;

	@Autowired
	SalesOrderRMAService salesOrderRMAService;

	@Autowired
	SalesOrderServiceV2 salesOrderServiceV2;

	@Autowired
	SalesOrderServiceV3 salesOrderServiceV3;
	
	@Autowired
	ConfigService configService;
	 
	@Autowired
	PromoService promoService;

	@Autowired
	InvoiceSharingService invoiceSharingService;

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("referral/list")
	public CustomerOrdersResponseDTO orderderListAll(@RequestHeader Map<String, String> httpRequestHeadrs) {

		return salesOrderService.getReferalOrderList(httpRequestHeadrs);

	}

	@PostMapping("mobile/order/list")
	public GenericApiResponse<MobileOrderListResponse> mobileOrderList(
			@RequestBody @Valid MobileOrderListRequest requestBody,
			@RequestHeader Map<String, String> requestHeader,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		return whatsappBotService.getMobileOrderList(requestBody, requestHeader, authorizationToken);

	}

	@PostMapping("mobile/order/details")
	public GenericApiResponse<MobileOrderDetailResponse> mobileOrderDetails(
			@RequestBody @Valid MobileOrderDetailRequest requestBody,
			@RequestHeader Map<String, String> requestHeader,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		return whatsappBotService.getMobileOrderDetails(requestBody, requestHeader, authorizationToken);

	}

	@PostMapping("mobile/order/list-updated")
	public GenericApiResponse<MobileShipmentListResponse> mobileOrderListUpdated(
			@RequestBody @Valid MobileOrderListRequest requestBody,
			@RequestHeader Map<String, String> requestHeader,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		return whatsappBotService.getMobileShipmentList(requestBody, requestHeader, authorizationToken);

	}

	@PostMapping("mobile/order/details-updated")
	public GenericApiResponse<MobileShipmentDetailResponse> mobileOrderDetailsUpdated(
			@RequestBody @Valid MobileOrderDetailRequest requestBody,
			@RequestHeader Map<String, String> requestHeader,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		return whatsappBotService.getMobileShipmentDetails(requestBody, requestHeader, authorizationToken);

	}
	
	@PostMapping("pendingpayment")
	public void makePaymentPendingOrdersToPaymentFailed(
			@RequestHeader(value = "authorization-token", required = true) String authorizationToken) {
		
		

		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (configService.checkAuthorization(authorizationToken, null)) {
				salesOrderService
				.makePaymentPendingOrdersToPaymentFailed();
			} else {
				throw new ForbiddenException();
			}
		} else {
			salesOrderService
			.makePaymentPendingOrdersToPaymentFailed();
		}
	}
	
	@PostMapping("payment-pending/notification")
	public void sendBrazeNotificatonForPendingOrder(
			@RequestHeader(value = "authorization-token", required = true) String authorizationToken) {
		
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (configService.checkAuthorization(authorizationToken, null)) {
				salesOrderService
				.sendBrazeNotificatonForPendingOrder();
			} else {
				throw new ForbiddenException();
			}
		} else {
			salesOrderService
			.sendBrazeNotificatonForPendingOrder();
		}
	}
	
	@PostMapping("failedpayment")
	public void makeFaildPendingOrder(
			@RequestHeader(value = "authorization-token", required = true) String authorizationToken) {
		
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (configService.checkAuthorization(authorizationToken, null)) {
				salesOrderService
				.makefailedNonHoldOrders();
			} else {
				throw new ForbiddenException();
			}
		} else {
			salesOrderService
			.makefailedNonHoldOrders();
		}
	}
	
	@PostMapping("mobile/return/list")
	public GenericApiResponse<MobileOrderListResponse> mobileReturnList(
			@RequestBody @Valid MobileOrderListRequest requestBody,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		return whatsappBotService.getMobileReturnList(requestBody, authorizationToken, false);

	}
	
	@PostMapping("mobile/unpicked/returns")
	public GenericApiResponse<MobileOrderListResponse> unPickedReturns(
			@RequestBody @Valid MobileOrderListRequest requestBody,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		return whatsappBotService.getMobileReturnList(requestBody, authorizationToken, true);

	}

	@PostMapping("mobile/return/details")
	public GenericApiResponse<MobileReturnDetailResponse> mobileReturnDetails(
			@RequestBody @Valid MobileOrderDetailRequest requestBody,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		return whatsappBotService.getMobileReturnDetails(requestBody, authorizationToken);

	}
	
	
	@PostMapping("promo/redemption-missed")
	public void promoRedemptionMissed(
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		promoService.syncPromoRedemptionMissed(authorizationToken);
		

	}

	/**
	 * Retry failed invoice uploads – get invoice, convert to Base64, send to Logistiq.
	 *
	 * Designed to be called by GCP Cloud Scheduler. For each track with failed invoice upload
	 * (invoice_upload_status = 'FAILED', attempts &lt; 10) it:
	 * 1) Gets the invoice PDF via OMS generatePDF API (using internal token when called from GCP)
	 * 2) Converts the response to Base64
	 * 3) Sends the document to Logistiq (Alpha upload-shipment-document API)
	 *
	 * GCP Cloud Scheduler configuration:
	 * - Method: POST
	 * - URL: https://your-service-url/rest/order/oms/invoice/retry-upload
	 * - Header: authorization-token: YOUR_INTERNAL_TOKEN
	 * - Schedule: e.g. every 15–30 minutes
	 *
	 * Does not retry National ID uploads (handled separately).
	 *
	 * @param authorizationToken Internal authentication token (required)
	 */
	@PostMapping("invoice/retry-upload")
	public void retryFailedInvoiceUploads(
			@RequestHeader(value = "authorization-token", required = true) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable() && 
				!configService.checkAuthorization(authorizationToken, null)) {
			throw new ForbiddenException();
		}
		
		invoiceSharingService.retryFailedInvoiceUploads();
	}
	
}
