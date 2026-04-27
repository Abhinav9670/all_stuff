package org.styli.services.order.controller;

import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.db.product.exception.NotFoundException;
import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.helper.PaymentDtfHelper;
import org.styli.services.order.pojo.OrderSms;
import org.styli.services.order.pojo.cancel.CancelOrderInitResponseDTO;
import org.styli.services.order.pojo.cancel.CancelOrderRequest;
import org.styli.services.order.pojo.order.AddStoreCreditRequest;
import org.styli.services.order.pojo.order.CancelRMARequest;
import org.styli.services.order.pojo.order.RMAOrderInitV2ResponseDTO;
import org.styli.services.order.pojo.order.RMAOrderV2Request;
import org.styli.services.order.pojo.order.RMAOrderV2RequestWrapper;
import org.styli.services.order.pojo.request.Order.CreateOrderRequestV2;
import org.styli.services.order.pojo.request.Order.OrderupdateRequest;
import org.styli.services.order.pojo.request.Order.ReOrderRequest;
import org.styli.services.order.pojo.response.AddStoreCreditResponse;
import org.styli.services.order.pojo.response.OmsOrderupdateresponse;
import org.styli.services.order.pojo.response.Order.CreateOrderResponseDTO;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.service.SalesOrderCancelService;
import org.styli.services.order.service.SalesOrderRMAService;
import org.styli.services.order.service.SalesOrderRetryService;
import org.styli.services.order.service.SalesOrderService;
import org.styli.services.order.service.SalesOrderServiceV2;
import org.styli.services.order.service.SalesOrderServiceV3;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.GenericConstants;
import org.styli.services.order.utility.OrderConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;

/**
 * @author Umesh, 14/05/2020
 * @project product-service
 */

@RestController
@RequestMapping("/rest/order/auth/")
@Api(value = "/rest/order/auth/", produces = "application/json")
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderWriteController {

	private static final Log LOGGER = LogFactory.getLog(OrderWriteController.class);
	
	private static final String RETURN = "return";

	@Autowired
	SalesOrderCancelService salesOrderCancelService;

	@Autowired
	SalesOrderRetryService salesOrderRetryService;

	@Autowired
	SalesOrderRMAService salesOrderRMAService;

	@Autowired
	SalesOrderService salesOrderService;

	@Autowired
	SalesOrderServiceV2 salesOrderServiceV2;

	@Autowired
	SalesOrderServiceV3 salesOrderServiceV3;

	@Value("${order.jwt.flag}")
	String jwtFlag;

	@Autowired
	@Qualifier("withoutEureka")
	private RestTemplate restTemplate;

	@Autowired
	PaymentDtfHelper paymentDtfHelper;

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("cancel/init")
	public CancelOrderInitResponseDTO cancelOrderInit(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestBody @Valid CancelOrderRequest request) {
		if ("1".equals(jwtFlag)) {
			salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
		}
		return salesOrderCancelService.cancelOrderInit(request);
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("/cancel")
	public OrderResponseDTO cancelOrder(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestBody @Valid CancelOrderRequest request) {
		if ("1".equals(jwtFlag)) {
			salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
		}
		OrderResponseDTO response = salesOrderCancelService.cancelOrder(request);
		if (null != response && response.getStatus()) {
			salesOrderServiceV3.sendCancelOrderSmsAndEMail(request.getOrderId(), response.isRefund());
		}

		return response;
	}

	@ApiImplicitParams({
		@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
@PostMapping("/cancel-v2")
public OrderResponseDTO cancelOrderV2(@RequestHeader Map<String, String> httpRequestHeadrs,
		@RequestBody @Valid CancelOrderRequest request) {
	if ("1".equals(jwtFlag)) {
		salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
	}
	OrderResponseDTO response = request.getSplitOrderId() != null ? salesOrderCancelService.cancelOrderV2(request) : salesOrderCancelService.cancelOrder(request);
	if (null != response && response.getStatus()) {
		if(request.getSplitOrderId() != null){
			LOGGER.info("SEND SMS FOR CANCELLED TEMPLATE START WORKING" + request.getSplitOrderId());
			salesOrderServiceV3.sendCancelOrderSmsAndEMailForSplit(request.getSplitOrderId(), response.isRefund());
		}
		else {
			LOGGER.info("SEND SMS FOR CANCELLED TEMPLATE START WORKING" + request.getOrderId());
			salesOrderServiceV3.sendCancelOrderSmsAndEMail(request.getOrderId(), response.isRefund());
		}
	}

    return response;
}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("v2/rma/init")
	public RMAOrderInitV2ResponseDTO rmaOrderInitV2(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestBody @Valid RMAOrderV2Request request,
			@RequestHeader(value = "x-client-version", required = false) String xClientVersion) {

		if ("1".equals(jwtFlag)) {
			salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
		}

		return salesOrderRMAService.rmaOrderVersionTwoInit(request, xClientVersion);
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("v2/rma/returnFeeToPay")
	public Double rmaReturnFeeToPay(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestBody @Valid RMAOrderV2Request request,
			@RequestHeader(value = "x-client-version", required = false) String xClientVersion) {

		if ("1".equals(jwtFlag))
			salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());

		RMAOrderInitV2ResponseDTO rmaOrderInitV2ResponseDTO = salesOrderRMAService.rmaOrderVersionTwoInit(request,
				xClientVersion);
		double returnAmountToPay = 0.0;

		if (rmaOrderInitV2ResponseDTO != null && rmaOrderInitV2ResponseDTO.getResponse() != null) {
			double amountToBePaid = rmaOrderInitV2ResponseDTO.getResponse().getReturnAmountToBePay();
			if (amountToBePaid > 0) {
				returnAmountToPay = amountToBePaid;
			}
		}

		return returnAmountToPay;

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("v2/rma")
	public OrderResponseDTO rmaOrderV2(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestBody @Valid RMAOrderV2Request request,
			@RequestHeader(value = "x-client-version", required = false) String xClientVersion) {

		if ("1".equals(jwtFlag))
			salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());

		OrderResponseDTO resp = salesOrderRMAService.rmaOrderVersionTwo(request, xClientVersion);

		if (null != resp && resp.getStatus() && null != request.getIsDropOffRequest()
				&& !request.getIsDropOffRequest().booleanValue() && null != resp.getResponse()) {

			salesOrderServiceV3.sendSms(resp.getResponse().getRmaIncId(), RETURN,
					OrderConstants.SMS_TEMPLATE_RETURN_CREATE, null);
		} else if (null != resp && resp.getStatus() && null != request.getIsDropOffRequest()
				&& request.getIsDropOffRequest().booleanValue() && null != resp.getResponse()) {
			salesOrderServiceV3.createDropOff(resp.getResponse().getRmaIncId(), RETURN,
					OrderConstants.SMS_TEMPLATE_RETURN_DROP_OFF, resp);
			salesOrderServiceV3.sendSms(resp.getResponse().getRmaIncId(), RETURN,
					OrderConstants.SMS_TEMPLATE_RETURN_DROP_OFF, resp);
		}

		return resp;

	}


	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("rma/cancel")
	public OrderResponseDTO cancelRMAOrder(@RequestBody @Valid CancelRMARequest request) {

		return null;

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("v2/create")
	public CreateOrderResponseDTO createOrderFromQuote(@RequestHeader Map<String, String> requestHeader,
			@RequestBody @Valid CreateOrderRequestV2 request, @RequestHeader("Token") String tokenHeader,
			@RequestHeader(value = "x-source", required = false) String xSource,
			@RequestHeader(value = "x-header-token", required = false) String xHeaderToken,
			@RequestHeader(value = "x-client-version", required = false) String xClientVersion,
			@RequestHeader(value = "x-original-forwarded-for", required = false) String customerIp)
			throws NotFoundException {

		String ipAddress = customerIp;
		LOGGER.info("customerIp  : " + ipAddress);
		String deviceId = MapUtils.isNotEmpty(requestHeader) ? requestHeader.get(Constants.HEADER_DEVICE_ID) : null;
		LOGGER.info("deviceId " + deviceId);
		String incrementId = null;
		if (StringUtils.isBlank(request.getOrderIncrementId()) && !request.isRetryPaymentReplica()) {

			incrementId = salesOrderService.getOrderIncrementId(request.getStoreId());
		} else if (StringUtils.isNotBlank(request.getOrderIncrementId())) {

			incrementId = request.getOrderIncrementId();
		}

		if ("1".equals(jwtFlag) && null != request.getCustomerId() && Constants.IS_JWT_TOKEN_ENABLE) {

			salesOrderServiceV2.authenticateOrderCheck(requestHeader, request.getCustomerId());
		}

		CreateOrderResponseDTO response = salesOrderServiceV2.convertQuoteToOrderV2(request, tokenHeader, incrementId,
				xSource, requestHeader, xHeaderToken, xClientVersion, ipAddress, deviceId);

		if (null != response && response.isCodOrder()) {

			OrderSms ordersms = new OrderSms();
			if (null != response.getResponse()) {
				ordersms.setOrderid(response.getResponse().getOrderId());
			}

			paymentDtfHelper.publishToKafka(ordersms);
		}

		return response;

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true)
	})
	@PostMapping("v3/create")
	public CreateOrderResponseDTO createOrderFromQuoteV3(
			@RequestHeader Map<String, String> requestHeader,
			@RequestBody @Valid CreateOrderRequestV2 request,
			@RequestHeader("Token") String tokenHeader,
			@RequestHeader(value = "x-source", required = false) String xSource,
			@RequestHeader(value = "x-header-token", required = false) String xHeaderToken,
			@RequestHeader(value = "x-client-version", required = false) String xClientVersion,
			@RequestHeader(value = "x-original-forwarded-for", required = false) String customerIp)
			throws NotFoundException {

		String ipAddress = customerIp;
		LOGGER.info("customerIp  : " + ipAddress);
		String deviceId = MapUtils.isNotEmpty(requestHeader) ? requestHeader.get(Constants.HEADER_DEVICE_ID) : null;
		LOGGER.info("deviceId " + deviceId);
		String incrementId = null;

		if (StringUtils.isBlank(request.getOrderIncrementId()) && !request.isRetryPaymentReplica()) {
			incrementId = salesOrderService.getOrderIncrementId(request.getStoreId());
		} else if (StringUtils.isNotBlank(request.getOrderIncrementId())) {
			incrementId = request.getOrderIncrementId();
		}

		if ("1".equals(jwtFlag) && null != request.getCustomerId() && Constants.IS_JWT_TOKEN_ENABLE) {
			salesOrderServiceV2.authenticateOrderCheck(requestHeader, request.getCustomerId());
		}

		CreateOrderResponseDTO response = salesOrderServiceV2.convertQuoteToOrderV3(
				request, tokenHeader, incrementId, xSource, requestHeader, xHeaderToken, xClientVersion, ipAddress, deviceId
		);

		if (null != response && response.isCodOrder()) {
			OrderSms ordersms = new OrderSms();
			if (null != response.getResponse()) {
				ordersms.setOrderid(response.getResponse().getOrderId());
			}
			paymentDtfHelper.publishToKafka(ordersms);
		}

		return response;
	}


	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("v2/reorder")
	public OrderResponseDTO reOrderV2(@RequestHeader Map<String, String> requestHeader,
			@RequestBody @Valid ReOrderRequest request, @RequestHeader("Token") String tokenHeader,
			@RequestHeader("x-header-token") String xHeaderToken,
			@RequestHeader(value = "x-client-version", required = false) String xClientVersion,
			@RequestHeader(value = "x-source", required = false) String xSource) throws NotFoundException {

		if ("1".equals(jwtFlag))
			salesOrderServiceV2.authenticateCheck(requestHeader, request.getCustomerId());

		return salesOrderRetryService.reOrderV2(requestHeader, request, GenericConstants.REORDER_MODE_IS_REORDER,
				tokenHeader, xHeaderToken, xSource, xClientVersion, restTemplate);

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PutMapping("v2/address/update")
	public OmsOrderupdateresponse updateOrderAddress(@RequestHeader Map<String, String> requestHeader,
													 @RequestBody @Valid OrderupdateRequest request, @RequestHeader("Token") String tokenHeader,
													 @RequestHeader("x-header-token") String xHeaderToken,
													 @RequestHeader(value = "x-client-version", required = false) String xClientVersion,
													 @RequestHeader(value = "x-source", required = false) String xSource) throws NotFoundException {

		if ("1".equals(jwtFlag))
			salesOrderServiceV2.authenticateCheck(requestHeader, request.getCustomerId());

		return salesOrderService.omsOrderaddressupdate(request, requestHeader);

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("v2/retrypayment")
	public OrderResponseDTO retryPaymentOrderV2(@RequestHeader Map<String, String> requestHeader,
			@RequestBody @Valid ReOrderRequest request, @RequestHeader("Token") String tokenHeader,
			@RequestHeader("x-header-token") String xHeaerToken,
			@RequestHeader(value = "x-client-version", required = false) String xClientVersion,
			@RequestHeader(value = "x-source", required = false) String xSource) throws NotFoundException {

		if ("1".equals(jwtFlag)) {
			salesOrderServiceV2.authenticateCheck(requestHeader, request.getCustomerId());
		}
		return salesOrderRetryService.reOrderV2(requestHeader, request, GenericConstants.REORDER_MODE_IS_RETRY_PAYMEWNT,
				tokenHeader, xHeaerToken, xSource, xClientVersion, restTemplate);

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("storecredit/add")
	public AddStoreCreditResponse addStoreCredit(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestBody @Valid AddStoreCreditRequest request) {

		request.setUpdateRequestType("bulk");

		return salesOrderServiceV2.addStoreCredit(request);

	}

}
