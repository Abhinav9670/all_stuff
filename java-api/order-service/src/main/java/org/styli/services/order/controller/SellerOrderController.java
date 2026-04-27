package org.styli.services.order.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.styli.services.order.config.aop.ConfigurableDatasource;
import org.styli.services.order.db.product.config.firebase.FirebaseAuthentication;
import org.styli.services.order.helper.OrderpushHelper;
import org.styli.services.order.pojo.OrderunfulfilmentRequest;
import org.styli.services.order.pojo.order.*;
import org.styli.services.order.pojo.request.Order.OrderViewRequest;
import org.styli.services.order.pojo.response.BulkShipmentResponse;
import org.styli.services.order.pojo.response.OmsOrderoutboundresponse;
import org.styli.services.order.pojo.response.Order.OmsUnfulfilmentResponse;
import org.styli.services.order.pojo.response.V3.GetShipmentV3Response;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.service.*;
import org.styli.services.order.service.impl.ZatcaServiceImpl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.RmaUtil;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sendgrid.Response;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;

@RestController
@RequestMapping("/rest/order/")
@Api(value = "/rest/order/auth/", produces = "application/json")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SellerOrderController {

	private static final Log LOGGER = LogFactory.getLog(SellerOrderController.class);

	private static final String RETURN = "return";

	@Autowired
	SalesOrderService salesOrderService;

	@Autowired
	CommonService commonService;

	@Autowired
	SalesOrderRetryService salesOrderRetryService;

	@Autowired
	SalesOrderRMAService salesOrderRMAService;

	@Autowired
	SalesOrderServiceV2 salesOrderServiceV2;

	@Autowired
	SalesOrderServiceV3 salesOrderServiceV3;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	ConfigService configService;

	@Autowired
	SalesOrderCustomerService salesOrderCustomerService;

	@Autowired
	SellerOrderService sellerOrderService;

	@Autowired
	private FirebaseAuthentication firebaseAuthentication;

	@Autowired
	RmaUtil rmaUtil;

	@Value("${order.jwt.flag}")
	String jwtFlag;

	@Value("${env}")
	private String env;

	@Autowired
	EmailService emailService;

	@Autowired
	OrderpushHelper orderpushHelper;

	@Autowired
	ZatcaServiceImpl zatcaServiceImpl;

	@Autowired
	SalesCreditmemoRepository salesCreditmemoRepository;

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("seller/shipment/create")
	@ConfigurableDatasource
	public OmsOrderoutboundresponse createSellerShipment(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid OrderViewRequest request,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable() || Constants.orderCredentials.isExternalAuthEnable()) {

			if ((Constants.orderCredentials.isInternalAuthEnable()
					&& configService.checkAuthorizationInternal(authorizationToken))
					|| (Constants.orderCredentials.isExternalAuthEnable()
							&& configService.checkAuthorizationExternal(authorizationToken))) {
				return sellerOrderService.crerateSellerShipment(request);

			} else {

				OmsOrderoutboundresponse response = new OmsOrderoutboundresponse();
				response.setStatus(false);
				response.setHasError(true);
				response.setStatusMsg(HttpStatus.UNAUTHORIZED.toString());

				return response;
			}
		} else {
			return sellerOrderService.crerateSellerShipment(request);
		}
	}


	@ApiImplicitParams({
		@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("seller/shipment/create/v2")
	@ConfigurableDatasource
	public BulkShipmentResponse createSellerShipmentV2(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid OrderViewRequest request,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable() || Constants.orderCredentials.isExternalAuthEnable()) {

			if ((Constants.orderCredentials.isInternalAuthEnable()
					&& configService.checkAuthorizationInternal(authorizationToken))
					|| (Constants.orderCredentials.isExternalAuthEnable()
							&& configService.checkAuthorizationExternal(authorizationToken))) {
				return sellerOrderService.createBulkSellerShipmentsV2(request);

			} else {	

				BulkShipmentResponse response = new BulkShipmentResponse();
				response.setStatus(false);
				response.setStatusCode("401");
				response.setStatusMsg(HttpStatus.UNAUTHORIZED.toString());
				response.setShipments(new ArrayList<>());
				response.setTotalRequested(0);
				response.setSuccessCount(0);
				response.setFailureCount(0);
				return response;
			}
		} else {
			return sellerOrderService.createBulkSellerShipmentsV2(request);
		}
	}

	@ApiImplicitParams({
		@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PutMapping("seller/cancel")
	@ConfigurableDatasource
	public OmsUnfulfilmentResponse createSellerCancellation(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid OrderunfulfilmentRequest request,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

        OmsUnfulfilmentResponse response = null;
		if (Constants.orderCredentials.isInternalAuthEnable() || Constants.orderCredentials.isExternalAuthEnable()) {

			if ((Constants.orderCredentials.isInternalAuthEnable()
					&& configService.checkAuthorizationInternal(authorizationToken))
					|| (Constants.orderCredentials.isExternalAuthEnable()
							&& configService.checkAuthorizationExternal(authorizationToken))) {
				response = sellerOrderService.createSellerCancellation(request, httpRequestHeaders);

			} else {
				response = new OmsUnfulfilmentResponse();
				response.setHasError(true);
				response.setErrorMessage(HttpStatus.UNAUTHORIZED.toString());

				return response;
			}
		} else {
			response = sellerOrderService.createSellerCancellation(request, httpRequestHeaders);
		}
        if (null != response && !response.getHasError()) {
            String codAmountforSms = response.getTotalCodCancelledAmount();
            salesOrderServiceV3.sendCancelOrderSmsAndEMail(request, codAmountforSms);
        }
        return response;
	}

	@ApiImplicitParams({
		@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@GetMapping(value = "/oms/seller/get-shipment-v3")
	@ConfigurableDatasource
	public GetShipmentV3Response getSellerShipmentV3(@RequestHeader Map<String, String> httpRequestHeaders,
		@RequestParam String orderCode, @RequestParam String shipmentCode,
		@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable() || Constants.orderCredentials.isExternalAuthEnable()) {

			if ((Constants.orderCredentials.isInternalAuthEnable()
					&& configService.checkAuthorizationInternal(authorizationToken))
					|| (Constants.orderCredentials.isExternalAuthEnable()
							&& configService.checkAuthorizationExternal(authorizationToken))) {

				LOGGER.info("authorizationToken:" + authorizationToken);
				return sellerOrderService.getSellerShipmentV3(orderCode, shipmentCode);

			} else {

				GetShipmentV3Response response = new GetShipmentV3Response();
				response.setHasError(true);
				response.setErrorMessage(HttpStatus.UNAUTHORIZED.toString());

				return response;
			}
		} else {
			return sellerOrderService.getSellerShipmentV3(orderCode, shipmentCode);
		}
	}

	// Create a new endpoint to get the seller orders of last 24 hours (time configurable through consul) where awb_failed = 1 and call the /oms/seller/get-shipment-v3 for them
	@ApiImplicitParams({
		@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping(value = "/oms/seller/update-awb-failed-shipments")
	@ConfigurableDatasource
	public ResponseEntity<Void> updateAwbFailedShipments(@RequestHeader Map<String, String> httpRequestHeaders,
		@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable() || Constants.orderCredentials.isExternalAuthEnable()) {

			if ((Constants.orderCredentials.isInternalAuthEnable()
					&& configService.checkAuthorizationInternal(authorizationToken))
					|| (Constants.orderCredentials.isExternalAuthEnable()
							&& configService.checkAuthorizationExternal(authorizationToken))) {
				sellerOrderService.updateAwbFailedShipments(httpRequestHeaders);
			} else {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			}
		} else {
			sellerOrderService.updateAwbFailedShipments(httpRequestHeaders);
		}
		return ResponseEntity.ok().build();
	}
}
