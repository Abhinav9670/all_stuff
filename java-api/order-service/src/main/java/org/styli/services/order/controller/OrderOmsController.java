package org.styli.services.order.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.Response;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.styli.services.order.config.aop.ConfigurableDatasource;
import org.styli.services.order.db.product.config.firebase.FirebaseAuthentication;
import org.styli.services.order.db.product.config.firebase.FirebaseUser;
import org.styli.services.order.db.product.exception.ForbiddenException;
import org.styli.services.order.exception.RollbackException;
import org.styli.services.order.helper.OrderpushHelper;
import org.styli.services.order.model.sales.*;
import org.styli.services.order.pojo.*;
import org.styli.services.order.pojo.oms.BankSubmitFormRequest;
import org.styli.services.order.pojo.oms.BankSwiftCodeMapperResponse;
import org.styli.services.order.pojo.order.*;
import org.styli.services.order.pojo.recreate.RecreateOrder;
import org.styli.services.order.pojo.recreate.RecreateOrderResponseDTO;
import org.styli.services.order.pojo.request.LockAndUnlockShukranRequest;
import org.styli.services.order.pojo.request.Order.*;
import org.styli.services.order.pojo.response.AddStoreCreditResponse;
import org.styli.services.order.pojo.response.LockAndUnlockShukranResponse;
import org.styli.services.order.pojo.response.OmsOrderoutboundresponse;
import org.styli.services.order.pojo.response.OmsOrderupdateresponse;
import org.styli.services.order.pojo.response.Order.*;
import org.styli.services.order.pojo.response.Order.OrderTrackingResponse.OrderTrackingResponseBuilder;
import org.styli.services.order.pojo.response.V3.GetInvoiceV3Response;
import org.styli.services.order.pojo.response.V3.GetShipmentV3Response;
import org.styli.services.order.repository.SalesOrder.SalesCreditmemoRepository;
import org.styli.services.order.repository.SalesOrder.SalesOrderRepository;
import org.styli.services.order.repository.SalesOrder.SellerBackOrderItemRepository;
import org.styli.services.order.repository.SalesOrder.SplitSalesOrderRepository;
import org.styli.services.order.service.*;
import org.styli.services.order.service.impl.SalesOrderCancelServiceImpl;
import org.styli.services.order.service.impl.ZatcaServiceImpl;
import org.styli.services.order.utility.Constants;
import org.styli.services.order.utility.OrderConstants;
import org.styli.services.order.utility.PaymentUtility;
import org.styli.services.order.utility.RmaUtil;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.stream.Collectors;

@RestController

@RequestMapping("/rest/order/")
@Api(value = "/rest/order/auth/", produces = "application/json")
@JsonIgnoreProperties(ignoreUnknown = true)

public class OrderOmsController {

	private static final Log LOGGER = LogFactory.getLog(OrderOmsController.class);

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
	SalesOrderCancelServiceImpl salesOrderCancelServiceImpl;

	@Autowired
	SellerBackOrderItemRepository sellerBackOrderItemRepository;

	@Autowired
	SalesOrderRepository salesOrderRepository;

	@Autowired
	SplitSalesOrderRepository splitSalesOrderRepository;

	@Autowired
	ConfigService configService;

	@Autowired
	SalesOrderCustomerService salesOrderCustomerService;

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

	@Autowired
	PaymentUtility paymentUtility;

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/list")
	@ConfigurableDatasource
	public CustomerOrdersResponseDTO orderListAll(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid OmsOrderListRequest request, HttpServletRequest req) {
		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
				return salesOrderService.getCustomerOmsOrders(request);
			} else {
				CustomerOrdersResponseDTO resp = new CustomerOrdersResponseDTO();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			return salesOrderService.getCustomerOmsOrders(request);
		}

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/details")
	@ConfigurableDatasource
	public OmsOrderresponsedto orderDetails(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid OrderViewRequest request,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable()) {

			if (configService.checkAuthorization(authorizationToken, null)) {

				return salesOrderService.getOmsOrderDetails(request);
			} else {
				OmsOrderresponsedto resp = new OmsOrderresponsedto();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			return salesOrderService.getOmsOrderDetails(request);
		}

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/invoice/details")
	@ConfigurableDatasource
	public OmsOrderresponsedto orderInvoiceDetails(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid OrderViewRequest request, HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			if (configService.checkAuthorization(authorizationToken, null)) {
				return salesOrderService.getOmsOrderInvoiceDetails(request);
			} else {
				OmsOrderresponsedto resp = new OmsOrderresponsedto();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			return salesOrderService.getOmsOrderInvoiceDetails(request);
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/shipping/details")
	@ConfigurableDatasource
	public OmsOrderresponsedto orderShippingDetails(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid OrderViewRequest request, HttpServletRequest req) {

		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
				return salesOrderService.getOmsOrderShippingDetails(request);
			} else {
				OmsOrderresponsedto resp = new OmsOrderresponsedto();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			return salesOrderService.getOmsOrderShippingDetails(request);
		}

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PutMapping("oms/status/update")
	@ConfigurableDatasource
	public OmsOrderupdateresponse updateOrderStatus(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid OrderupdateRequest request, HttpServletRequest req) {
		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
				return salesOrderService.omsStatusupdate(request);
			} else {
				OmsOrderupdateresponse resp = new OmsOrderupdateresponse();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			return salesOrderService.omsStatusupdate(request);
		}

	}

	@ApiImplicitParams({
		@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
@PutMapping("oms/status/update/v2")
@ConfigurableDatasource
public OmsOrderupdateresponse updateOrderStatusV2(@RequestHeader Map<String, String> httpRequestHeaders,
		@RequestBody @Valid OrderupdateRequest request, HttpServletRequest req) {
	if (Constants.orderCredentials.isFirebaseAuthEnable()) {

		firebaseAuthentication.verifyToken(req);
		Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		if (obj instanceof FirebaseUser) {
			return salesOrderService.omsStatusupdate(request);
		} else {
			OmsOrderupdateresponse resp = new OmsOrderupdateresponse();
			resp.setStatus(false);
			resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
			return resp;
		}
	} else {
		return salesOrderService.omsStatusupdate(request);
	}

}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PutMapping("oms/address/update")
	@ConfigurableDatasource
	public OmsOrderupdateresponse updateOrderAddress(@RequestHeader Map<String, String> httpRequestHeaders,
													 @RequestBody @Valid OrderupdateRequest request, HttpServletRequest req) {

		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
				return salesOrderService.omsOrderaddressupdate(request, httpRequestHeaders);
			} else {
				OmsOrderupdateresponse resp = new OmsOrderupdateresponse();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			return salesOrderService.omsOrderaddressupdate(request, httpRequestHeaders);
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/shipment/create")
	@ConfigurableDatasource
	public OmsOrderoutboundresponse createShipment(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid OrderViewRequest request,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable() || Constants.orderCredentials.isExternalAuthEnable()) {

			if ((Constants.orderCredentials.isInternalAuthEnable()
					&& configService.checkAuthorizationInternal(authorizationToken))
					|| (Constants.orderCredentials.isExternalAuthEnable()
							&& configService.checkAuthorizationExternal(authorizationToken))) {
				return salesOrderService.crerateShipment(request);

			} else {

				OmsOrderoutboundresponse response = new OmsOrderoutboundresponse();
				response.setStatus(false);
				response.setHasError(true);
				response.setStatusMsg(HttpStatus.UNAUTHORIZED.toString());

				return response;
			}
		} else {
			return salesOrderService.crerateShipment(request);
		}

	}

	@PostMapping("oms/storecredit/history")
	@ConfigurableDatasource
	public CreditHistoryResponse storeCreditHistory(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid OrderStoreCreditRequest request, HttpServletRequest req) {

		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
				return salesOrderService.getStoreCreditHistory(request);
			} else {
				CreditHistoryResponse resp = new CreditHistoryResponse();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			return salesOrderService.getStoreCreditHistory(request);
		}

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/storecredit/add")
	@ConfigurableDatasource
	public AddStoreCreditResponse addStoreCredit(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid AddStoreCreditRequest request, HttpServletRequest req) {

		request.setUpdateRequestType("oms");

		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
				return salesOrderServiceV2.addStoreCredit(request);
			} else {
				AddStoreCreditResponse resp = new AddStoreCreditResponse();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			return salesOrderServiceV2.addStoreCredit(request);
		}

	}

	@GetMapping(value = "/oms/get-shipment-v3")
	@ConfigurableDatasource
	public GetShipmentV3Response getShipmentV3(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestParam String orderCode, @RequestParam String shipmentCode,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable() || Constants.orderCredentials.isExternalAuthEnable()) {

			if ((Constants.orderCredentials.isInternalAuthEnable()
					&& configService.checkAuthorizationInternal(authorizationToken))
					|| (Constants.orderCredentials.isExternalAuthEnable()
							&& configService.checkAuthorizationExternal(authorizationToken))) {

				LOGGER.info("authorizationToken:" + authorizationToken);
				return salesOrderServiceV3.getShipmentV3(orderCode, shipmentCode);

			} else {

				GetShipmentV3Response response = new GetShipmentV3Response();
				response.setHasError(true);
				response.setErrorMessage(HttpStatus.UNAUTHORIZED.toString());

				return response;
			}
		} else {
			return salesOrderServiceV3.getShipmentV3(orderCode, shipmentCode);
		}

	}

	@GetMapping(value = "/oms/get-invoice-v3")
	@ConfigurableDatasource
	public GetInvoiceV3Response getInvoiceV3(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestParam String orderCode, @RequestParam String shipmentCode,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isExternalAuthEnable()) {

			if (configService.checkAuthorization(null, authorizationToken)) {

				return salesOrderServiceV3.getInvoiceV3(orderCode, shipmentCode);

			} else {

				GetInvoiceV3Response response = new GetInvoiceV3Response();
				response.setHasError(true);
				response.setErrorMessage(HttpStatus.UNAUTHORIZED.toString());

				return response;
			}
		} else {
			return salesOrderServiceV3.getInvoiceV3(orderCode, shipmentCode);
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/rma/update")
	@ConfigurableDatasource
	public OrderResponseDTO rmaUpdateV2(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid RMAUpdateV2Request request, HttpServletRequest req) {

		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
				return salesOrderServiceV3.rmaUpdateVersionTwo(request);
			} else {
				OrderResponseDTO resp = new OrderResponseDTO();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			return salesOrderServiceV3.rmaUpdateVersionTwo(request);
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/order-push")
	@ConfigurableDatasource
	public OmsOrderresponsedto orderPush(@RequestHeader Map<String, String> httpRequestHeaders, HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable()
				&& !configService.checkAuthorizationInternal(authorizationToken)) {
			LOGGER.info("You're not authenticated to make order push request.");
			throw new ForbiddenException();
		}
		OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();
		List<SalesOrder> orderList = salesOrderServiceV3.orderpushTowms();
		for (SalesOrder order : orderList) {
			LOGGER.info("Order increment ID for WMS status update :: "+ order.getIncrementId());
			try {
				orderpushHelper.orderpushTowms(Arrays.asList(order));
			} catch (Exception e) {
				LOGGER.error("exception during push to oms: for order :" + order.getIncrementId(), e);
			}
		}
		omsOrderresponsedto.setStatus(true);
		omsOrderresponsedto.setStatusCode("200");
		omsOrderresponsedto.setStatusMsg("pushed successfully");
		return omsOrderresponsedto;
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/order-push/v2")
	@ConfigurableDatasource
	public OmsOrderresponsedto orderPushV2(@RequestHeader Map<String, String> httpRequestHeaders, HttpServletRequest req,
										 @RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable()
				&& !configService.checkAuthorizationInternal(authorizationToken)) {
			LOGGER.info("You're not authenticated to make order push request.");
			throw new ForbiddenException();
		}
		OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();
		List<SplitSalesOrder> orderList = salesOrderServiceV3.orderpushTowmsV2();
		for (SplitSalesOrder splitSalesOrder : orderList) {
			LOGGER.info("splitSalesOrder increment ID for WMS status update :: "+ splitSalesOrder.getIncrementId());
			try {
				orderpushHelper.orderpushTowmsv2(Arrays.asList(splitSalesOrder));
			} catch (Exception e) {
				LOGGER.error("exception during push to oms: for splitSalesOrder :" + splitSalesOrder.getIncrementId(), e);
			}
		}
		omsOrderresponsedto.setStatus(true);
		omsOrderresponsedto.setStatusCode("200");
		omsOrderresponsedto.setStatusMsg("split sales order pushed successfully");
		return omsOrderresponsedto;
	}

	@ApiImplicitParams({
		@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
@PostMapping("oms/order-push/v3")
@ConfigurableDatasource
public OmsOrderresponsedto orderPushV3(@RequestHeader Map<String, String> httpRequestHeaders, HttpServletRequest req,
									 @RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

        if (Constants.orderCredentials.isInternalAuthEnable()
                && !configService.checkAuthorizationInternal(authorizationToken)) {
            LOGGER.info("You're not authenticated to make order push request.");
            throw new ForbiddenException();
        }
        OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();
        List<SplitSellerOrder> orderList = salesOrderServiceV3.orderpushTowmsV3();
        for (SplitSellerOrder splitSellerOrder : orderList) {
            List<SellerConfig> sellerConfig = orderpushHelper.getSellerConfigBySellerIdAndStyliWarehouseId(splitSellerOrder.getSellerId(), splitSellerOrder.getWarehouseId());
            if(CollectionUtils.isNotEmpty(sellerConfig)) {
                if (Boolean.TRUE.equals(sellerConfig.get(0).getIsB2BSeller())) {
                    continue;
                }
            }
            if (splitSellerOrder.getSplitOrder() == null || splitSellerOrder.getSplitOrder().getStoreId() == null) {
                LOGGER.info("[OrderOmsController] splitSellerOrder with sales order increment ID for WMS status update :: " + splitSellerOrder.getIncrementId());
                try {
                    salesOrderServiceV3.getSalesOrderForSellerOrder(splitSellerOrder);
                    orderpushHelper.orderpushTowmsv3(Arrays.asList(splitSellerOrder));
                } catch (Exception e) {
                    LOGGER.error("[OrderOmsController] exception during push to oms: for splitSellerOrder :" + splitSellerOrder.getIncrementId(), e);
                }
            } else {
                LOGGER.info("[OrderOmsController] splitSellerOrder with split order increment ID for WMS status update :: " + splitSellerOrder.getIncrementId());
                try {
                    orderpushHelper.orderpushTowmsv3(Arrays.asList(splitSellerOrder));
                } catch (Exception e) {
                    LOGGER.error("[OrderOmsController] exception during push to oms: for splitSellerOrder :" + splitSellerOrder.getIncrementId(), e);
                }
            }
        }
        omsOrderresponsedto.setStatus(true);
        omsOrderresponsedto.setStatusCode("200");
        omsOrderresponsedto.setStatusMsg("split seller order pushed successfully");
        return omsOrderresponsedto;
    }


	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/order-cancel")
	@ConfigurableDatasource
	public OmsOrderresponsedto orderCancel(@RequestHeader Map<String, String> httpRequestHeaders,
			HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
				throw new ForbiddenException();
			} else {
				return processCancelPushToWms();
			}
		} else {
			return processCancelPushToWms();
		}
	}

	@ApiImplicitParams({
		@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
		@PostMapping("oms/order-cancel/split")
		@ConfigurableDatasource
		public OmsOrderresponsedto orderCancelForSplitOrder(@RequestHeader Map<String, String> httpRequestHeaders,
				HttpServletRequest req,
				@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
			if (Constants.orderCredentials.isInternalAuthEnable()) {
				if (!configService.checkAuthorizationInternal(authorizationToken)) {
					LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
					throw new ForbiddenException();
				} else {
					return processCancelPushToWmsForSplitOrder();
				}
			} else {
				return processCancelPushToWmsForSplitOrder();
			}
		}

	@ApiImplicitParams({
		@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
		@PostMapping("oms/order-cancel/seller")
		@ConfigurableDatasource
		public OmsOrderresponsedto orderCancelForSellerOrder(@RequestHeader Map<String, String> httpRequestHeaders,
			HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
				if (Constants.orderCredentials.isInternalAuthEnable()) {
					if (!configService.checkAuthorizationInternal(authorizationToken)) {
						LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
						throw new ForbiddenException();
					} else {
						return processCancelPushToWmsForSellerOrder();
					}
				} else {
					return processCancelPushToWmsForSellerOrder();
				}
		}


	private OmsOrderresponsedto processCancelPushToWms() {
		OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();
		Map<Integer,List<WarehouseItem>> orderList = salesOrderServiceV3.orderWmsCancel();
		List<Integer> incrementIds = orderList.keySet().stream().toList();
		LOGGER.info("Orders to be cancelled in WMS : " + incrementIds);
		for (Map.Entry<Integer,List<WarehouseItem>> order : orderList.entrySet()) {
			try {
				orderpushHelper.orderCancelpushTowms(order);
			} catch (Exception e) {
				LOGGER.error("Error in cancel push. Error : ", e);
			}
		}
		omsOrderresponsedto.setStatus(true);
		omsOrderresponsedto.setStatusCode("200");
		omsOrderresponsedto.setStatusMsg("pushed successfully");

		return omsOrderresponsedto;
	}


	private OmsOrderresponsedto processCancelPushToWmsForSplitOrder() {
		OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();
		List<SplitSalesOrder> orderList = salesOrderServiceV3.orderWmsCancelForSplitOrder();
		List<String> incrementIds = orderList.stream().map(SplitSalesOrder::getIncrementId).collect(Collectors.toList());
		LOGGER.info("Orders to be cancelled in WMS : " + incrementIds);
		for (SplitSalesOrder order : orderList) {
			try {
				orderpushHelper.orderCancelpushTowmsForSplitOrder(order, null, new ArrayList<>());
			} catch (Exception e) {
				LOGGER.error("Error in cancel push. Error : ", e);
				throw e;
			}
		}
		omsOrderresponsedto.setStatus(true);
		omsOrderresponsedto.setStatusCode("200");
		omsOrderresponsedto.setStatusMsg("pushed successfully");

		return omsOrderresponsedto;
	}

	private OmsOrderresponsedto processCancelPushToWmsForSellerOrder() {
		OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();
		List<SplitSellerOrder> orderList = salesOrderServiceV3.orderWmsCancelForSellerOrder();
		List<String> incrementIds = orderList.stream().map(SplitSellerOrder::getIncrementId).collect(Collectors.toList());
		Map<SalesOrder, Map<Integer, SalesOrderItem>> styliWmsMainSkusMap = new HashMap<>();
		Map<SplitSalesOrder, Map<Integer, SplitSalesOrderItem>> styliWmsSplitSkusMap = new HashMap<>();
		LOGGER.info("Seller orders to be cancelled in WMS : " + incrementIds);
		
		for (SplitSellerOrder order : orderList) {
			try {
				processSellerOrderCancellation(order, styliWmsMainSkusMap, styliWmsSplitSkusMap);
			} catch (Exception e) {
				LOGGER.error("Error in cancel push. Error : ", e);
				throw e;
			}
		}
		
		pushCancelToStyliWms(styliWmsSplitSkusMap, styliWmsMainSkusMap, orderList);
		
		omsOrderresponsedto.setStatus(true);
		omsOrderresponsedto.setStatusCode("200");
		omsOrderresponsedto.setStatusMsg("pushed successfully");

		return omsOrderresponsedto;
	}

	private void processSellerOrderCancellation(SplitSellerOrder order, 
			Map<SalesOrder, Map<Integer, SalesOrderItem>> styliWmsMainSkusMap,
			Map<SplitSalesOrder, Map<Integer, SplitSalesOrderItem>> styliWmsSplitSkusMap) {
		
		List<SellerConfig> sellerConfigList = orderpushHelper.getSellerConfigBySellerIdAndStyliWarehouseId(
				order.getSellerId(), order.getWarehouseId());
		Optional<SellerConfig> sellerConfig = CollectionUtils.isNotEmpty(sellerConfigList)
				? Optional.of(sellerConfigList.get(0))
				: Optional.empty();

		if (sellerConfig.isPresent() && Boolean.TRUE.equals(sellerConfig.get().getIsB2BSeller())) {
			// Handle back orders only when split order is open/processing
			handleBackOrdersForSellerOrder(order);
			return;
		}
		
		// Process non-B2B seller orders
		processNonB2BSellerOrderCancellation(order, styliWmsMainSkusMap, styliWmsSplitSkusMap);
	}

	private void processNonB2BSellerOrderCancellation(SplitSellerOrder order,
			Map<SalesOrder, Map<Integer, SalesOrderItem>> styliWmsMainSkusMap,
			Map<SplitSalesOrder, Map<Integer, SplitSalesOrderItem>> styliWmsSplitSkusMap) {
		
		Integer cancelledBy = order.getSplitSellerOrderItems().stream()
				.map(SplitSellerOrderItem::getCancelledBy)
				.findFirst()
				.orElse(null);
		
		if (cancelledBy != OrderConstants.CANCELLED_BY_SELLER) {
			// Push to seller wms if not cancelled by seller
			orderpushHelper.orderCancelpushTowmsForSellerOrder(order);
			return;
		}
		
		// Push to styli wms if cancelled by seller or system
		if (cancelledBy == OrderConstants.CANCELLED_BY_SELLER || cancelledBy == OrderConstants.CANCELLED_BY_SYSTEM) {
			collectItemsForStyliWms(order, styliWmsMainSkusMap, styliWmsSplitSkusMap);
		}
	}

	private void collectItemsForStyliWms(SplitSellerOrder order,
			Map<SalesOrder, Map<Integer, SalesOrderItem>> styliWmsMainSkusMap,
			Map<SplitSalesOrder, Map<Integer, SplitSalesOrderItem>> styliWmsSplitSkusMap) {
		
		order.getSplitSellerOrderItems().stream()
				.filter(item -> !item.getProductType().equalsIgnoreCase(OrderConstants.ORDER_ITEM_CONFIGURABLE_TYPE))
				.forEach(item -> {
					// In this case, there may be a SKU which was part of multiple seller orders
					// e.g. 2 seller orders: 1 cancelled by styli, other by seller
					// We are pushing all the qty to styli wms
					if (order.getSplitOrder() != null) {
						addSplitOrderItemToMap(item, order.getSplitOrder(), styliWmsSplitSkusMap);
					} else {
						addMainOrderItemToMap(item, order.getSalesOrder(), styliWmsMainSkusMap);
					}
				});
	}

	private void addSplitOrderItemToMap(SplitSellerOrderItem item, SplitSalesOrder splitOrder,
			Map<SplitSalesOrder, Map<Integer, SplitSalesOrderItem>> styliWmsSplitSkusMap) {
		
		SplitSalesOrderItem splitSalesOrderItem = item.getSplitSalesOrderItem();
		if (splitSalesOrderItem == null || splitSalesOrderItem.getItemId() == null) {
			return;
		}
		
		Map<Integer, SplitSalesOrderItem> splitSalesOrderItemsMap = styliWmsSplitSkusMap.getOrDefault(
				splitOrder, new HashMap<>());
		
		// Only add if itemId doesn't already exist to avoid duplicates
		if (!splitSalesOrderItemsMap.containsKey(splitSalesOrderItem.getItemId())) {
			splitSalesOrderItemsMap.put(splitSalesOrderItem.getItemId(), splitSalesOrderItem);
			styliWmsSplitSkusMap.put(splitOrder, splitSalesOrderItemsMap);
		}
	}

	private void addMainOrderItemToMap(SplitSellerOrderItem item, SalesOrder salesOrder,
			Map<SalesOrder, Map<Integer, SalesOrderItem>> styliWmsMainSkusMap) {
		
		SalesOrderItem salesOrderItem = item.getSalesOrderItem();
		if (salesOrderItem == null || salesOrderItem.getItemId() == null) {
			return;
		}
		
		Map<Integer, SalesOrderItem> salesOrderItemsMap = styliWmsMainSkusMap.getOrDefault(
				salesOrder, new HashMap<>());
		
		// Only add if itemId doesn't already exist to avoid duplicates
		if (!salesOrderItemsMap.containsKey(salesOrderItem.getItemId())) {
			salesOrderItemsMap.put(salesOrderItem.getItemId(), salesOrderItem);
			styliWmsMainSkusMap.put(salesOrder, salesOrderItemsMap);
		}
	}

	private void pushCancelToStyliWms(Map<SplitSalesOrder, Map<Integer, SplitSalesOrderItem>> styliWmsSplitSkusMap,
			Map<SalesOrder, Map<Integer, SalesOrderItem>> styliWmsMainSkusMap,
			List<SplitSellerOrder> orderList) {
		
		for (Map.Entry<SplitSalesOrder, Map<Integer, SplitSalesOrderItem>> entry : styliWmsSplitSkusMap.entrySet()) {
			orderpushHelper.orderCancelpushTowmsForSplitOrder(entry.getKey(), 
					new ArrayList<>(entry.getValue().values()), orderList);
		}
		
		for (Map.Entry<SalesOrder, Map<Integer, SalesOrderItem>> entry : styliWmsMainSkusMap.entrySet()) {
			orderpushHelper.orderCancelpushTowmsV2(entry.getKey(), 
					new ArrayList<>(entry.getValue().values()), orderList);
		}
	}

	private void handleBackOrdersForSellerOrder(SplitSellerOrder sellerOrder) {
		if (sellerOrder == null) {
			return;
		}
		
		// Check if there are any non-CLOSED back order items for this seller order
		List<SellerBackOrderItem> backOrderItems = sellerBackOrderItemRepository
				.findBySplitSellerOrderAndStatusNot(sellerOrder, "CLOSED");
		
		if (CollectionUtils.isEmpty(backOrderItems)) {
			LOGGER.info("[BackOrder][SellerCancel] No active back order items found for seller order: " + sellerOrder.getIncrementId());
			return;
		}
		
		// Process back order items - the service method will check parent back order status for each item
		if (sellerOrder.getSplitSellerOrderItems() != null) {
			LOGGER.info("[BackOrder][SellerCancel] Processing back order items for seller order: " + sellerOrder.getIncrementId());
			salesOrderCancelServiceImpl.processBackOrderItemsOnCancelForSplitSellerOrder(sellerOrder);
		} else {
			LOGGER.info("[BackOrder][SellerCancel] Skipping back order push because split order reference is missing for seller order: "
					+ sellerOrder.getIncrementId());
		}
	}


	@ApiOperation(value = "Process seller-initiated unfulfilment or cancellation and notify customer.")
	@PutMapping("oms/sellercancellation")
	@ConfigurableDatasource
	public OmsUnfulfilmentResponse updateUnfulfilmentOrder(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestBody @Valid OrderunfulfilmentRequest request,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		try {
			OmsUnfulfilmentResponse response = null;
			if (Constants.orderCredentials.isExternalAuthEnable()) {

				if (configService.checkAuthorization(null, authorizationToken)) {

					response = salesOrderServiceV3.updateUnfulfilmentOrder(request, httpRequestHeadrs);
					LOGGER.info("Seller Cancelation Response : " + response);
					if (null != response && !response.getHasError()) {
						String codAmountforSms = response.getTotalCodCancelledAmount();
						salesOrderServiceV3.sendCancelOrderSmsAndEMail(request, codAmountforSms);
					}
					if (Objects.nonNull(response))
						response.setTotalCodCancelledAmount(null);
				} else {
					response = new OmsUnfulfilmentResponse();
					response.setHasError(true);
					response.setErrorMessage(HttpStatus.UNAUTHORIZED.toString());
					return response;
				}
			} else {

				response = salesOrderServiceV3.updateUnfulfilmentOrder(request, httpRequestHeadrs);
				LOGGER.info("Seller Cancelation Response : " + response);
				if (null != response && !response.getHasError()) {
					String codAmountforSms = response.getTotalCodCancelledAmount();
					salesOrderServiceV3.sendCancelOrderSmsAndEMail(request, codAmountforSms);
				}
				if (Objects.nonNull(response))
					response.setTotalCodCancelledAmount(null);
			}
			return response;
		} catch (Exception e) {
			LOGGER.info("Error In Seller Cancelation "+ e.getMessage());
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("rawtypes")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping(value = "oms/dtf", consumes = MediaType.ALL_VALUE)
	@ConfigurableDatasource
	public ResponseEntity payfortDtf(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestParam Map<String, String> requestObject) {
		return salesOrderServiceV3.dtfCall(httpRequestHeadrs, requestObject);
	}

	@PostMapping(value = "oms/cod/rto/zatca")
	@ConfigurableDatasource
	public OmsRtoCodResponse omsRtoCodResponse(@RequestHeader Map<String, String> httpRequestHeaders,
									 @RequestBody @Valid OmsRtoCodRequest request) {
		return salesOrderServiceV3.CreateOmsRtoOrderZatca(httpRequestHeaders, request);

	}


	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/rma")
	@ConfigurableDatasource
	public OrderResponseDTO rmaOrderV2(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestBody @Valid RMAOrderV2Request request, HttpServletRequest req) {
		OrderResponseDTO resp = null;
		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {

				resp = salesOrderRMAService.rmaOrderVersionTwo(request, "");

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

			} else {

				throw new ForbiddenException();
			}

		} else {

			resp = salesOrderRMAService.rmaOrderVersionTwo(request, "");

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

		}

		return resp;

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@ConfigurableDatasource

	@PostMapping("oms/rma/init")
	public RMAOrderInitV2ResponseDTO rmaOrderInitV2(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid RMAOrderV2Request request, HttpServletRequest req) {

		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
				return salesOrderRMAService.rmaOrderVersionTwoInit(request, "");
			} else {
				RMAOrderInitV2ResponseDTO resp = new RMAOrderInitV2ResponseDTO();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			return salesOrderRMAService.rmaOrderVersionTwoInit(request, "");
		}

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping(value = "oms/return/refund")
	@ConfigurableDatasource
	public RefundPaymentRespone payfortRefund(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid payFortRefund request, HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		LOGGER.info("inside payfort refund controller");

		if (Constants.orderCredentials.isInternalAuthEnable()) {

			if (configService.checkAuthorization(authorizationToken, null)) {
				return salesOrderServiceV3.payfortRefundCall(httpRequestHeaders, request);

			} else {

				throw new ForbiddenException();
			}
		} else {
			return salesOrderServiceV3.payfortRefundCall(httpRequestHeaders, request);
		}
	}

	@GetMapping("oms/return/get-awb/requestid/{requestid}")
	@ConfigurableDatasource
	public GetShipmentV3Response getReturnShipment(@RequestHeader Map<String, String> httpRequestHeaders,
			@PathVariable String requestid, HttpServletRequest req) {

		GetShipmentV3Response response = null;
		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
				response = salesOrderServiceV3.getReturnShipment(httpRequestHeaders, requestid);
				if (null != response && !response.isHasError()) {

					salesOrderServiceV3.sendSms(requestid, "RETURN", OrderConstants.SMS_TEMPLATE_RETURN_AWB_CREATE,
							null);
				}
			} else {
				GetShipmentV3Response resp = new GetShipmentV3Response();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
			response = salesOrderServiceV3.getReturnShipment(httpRequestHeaders, requestid);
			if (null != response && !response.isHasError()) {

				salesOrderServiceV3.sendSms(requestid, "RETURN", OrderConstants.SMS_TEMPLATE_RETURN_AWB_CREATE, null);
			}
		}

		return response;
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping(value = "oms/recreate")
	@ConfigurableDatasource
	public RecreateOrderResponseDTO recreateInit(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid RecreateOrder request, HttpServletRequest req) {

		LOGGER.info("inside recreateInit in OrderOmsController");
		SalesOrder order = null;
		SplitSalesOrder splitOrder = null;
		String incrementId = null;

		if (Constants.orderCredentials.isFirebaseAuthEnable()) {

			firebaseAuthentication.verifyToken(req);
			Object obj = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
			if (obj instanceof FirebaseUser) {
                if(!request.getIsSplitOrder()){
                    order = salesOrderRepository.findByEntityId(request.getOrderId());
                }
                else {
					List<SplitSalesOrder> splitSalesOrders = splitSalesOrderRepository.findByOrderId(request.getOrderId());
                	splitOrder = splitSalesOrders.stream().filter(e -> e.getEntityId().equals(request.getSplitOrderId())).findFirst().orElse(null);
                }
				if (order == null && splitOrder == null) {
					RecreateOrderResponseDTO resp = new RecreateOrderResponseDTO();
					resp.setStatus(false);
					resp.setStatusCode("202");
					resp.setStatusMsg("Order not found!");
					return resp;
				}
				if (request.getIsSubmit().booleanValue()) {
					Integer storeId = order != null ? order.getStoreId() : (splitOrder != null ? splitOrder.getStoreId() : null);
					incrementId = salesOrderService.getOrderIncrementId(storeId);
				}
				if (order != null) {
					return salesOrderServiceV3.recreateOrder(httpRequestHeaders, request, incrementId, order);
				} else {
					return salesOrderServiceV3.recreateOrderForSplitOrder(httpRequestHeaders, request, incrementId, splitOrder);
				}
			} else {
				RecreateOrderResponseDTO resp = new RecreateOrderResponseDTO();
				resp.setStatus(false);
				resp.setStatusCode(HttpStatus.UNAUTHORIZED.toString());
				return resp;
			}
		} else {
            if(!request.getIsSplitOrder()){
                order = salesOrderRepository.findByEntityId(request.getOrderId());
            }
            else {
                List<SplitSalesOrder> splitSalesOrders = splitSalesOrderRepository.findByOrderId(request.getOrderId());
				splitOrder = splitSalesOrders.stream().filter(e -> e.getEntityId().equals(request.getSplitOrderId())).findFirst().orElse(null);
            }
			if (order == null && splitOrder == null) {
				RecreateOrderResponseDTO resp = new RecreateOrderResponseDTO();
				resp.setStatus(false);
				resp.setStatusCode("202");
				resp.setStatusMsg("Order not found!");
				return resp;
			}
			if (request.getIsSubmit().booleanValue()) {
				Integer storeId = order != null ? order.getStoreId() : (splitOrder != null ? splitOrder.getStoreId() : null);
				incrementId = salesOrderService.getOrderIncrementId(storeId);
			}
			if (order != null) {
				return salesOrderServiceV3.recreateOrder(httpRequestHeaders, request, incrementId, order);
			} else {
				return salesOrderServiceV3.recreateOrderForSplitOrder(httpRequestHeaders, request, incrementId, splitOrder);
			}
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/rma/awb-creation")
	@ConfigurableDatasource
	public GetShipmentV3Response rmaAwbCreation(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		GetShipmentV3Response getShipmentV3Response;
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				throw new ForbiddenException();
			} else {
				getShipmentV3Response = salesOrderServiceV3.rmaAwbCreation(httpRequestHeaders);
			}
		} else {
			getShipmentV3Response = salesOrderServiceV3.rmaAwbCreation(httpRequestHeaders);
		}

		return getShipmentV3Response;
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping(value = "oms/payfort/query")
	public RefundPaymentRespone queryDtf(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
				throw new ForbiddenException();
			} else {
				return processPayfortQuery(httpRequestHeadrs.get(Constants.deviceId));
			}
		} else {
			return processPayfortQuery(httpRequestHeadrs.get(Constants.deviceId));
		}
	}

	private RefundPaymentRespone processPayfortQuery(String deviceId) {
		RefundPaymentRespone refundPaymentRespone = null;
		List<SalesOrder> orderList = salesOrderServiceV3.payfortQueryFetch();
		LOGGER.info("Order to be processed for payfort Query. size : " + orderList.size());
		if (CollectionUtils.isNotEmpty(orderList)) {
			for (SalesOrder order : orderList) {
				LOGGER.info("Processing Order ID: " + order.getIncrementId());
				salesOrderServiceV3.payfortQueryUpdate(order, deviceId);
				paymentUtility.publishToSplitPubSubOTSForSalesOrder(order,null,null);
			}
		}
		refundPaymentRespone = new RefundPaymentRespone();
		refundPaymentRespone.setStatus(true);
		refundPaymentRespone.setStatusCode("200");
		return refundPaymentRespone;
	}

	@PostMapping("oms/salesorder/updatecustomerid")
	@ConfigurableDatasource
	public void updateSalesOrdersWithCustomerId() {
		salesOrderCustomerService
				.updateSalesOrdersCustomerId(Constants.orderCredentials.getSalesOrderUpdateCustomerIdHours());
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/rma/dropoff/awb-creation")
	public GetShipmentV3Response rmaAwbDropoffCreation(@RequestHeader Map<String, String> httpRequestHeaders) {

		return salesOrderServiceV3.rmaAwbDropOffCreation(httpRequestHeaders);
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@GetMapping("oms/bank/mapper")
	@ConfigurableDatasource
	public BankSwiftCodeMapperResponse bankSwiftCodeMapper(@RequestHeader Map<String, String> httpRequestHeaders) {

		return salesOrderServiceV3.getBankSwiftCodes(httpRequestHeaders);
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("auth/oms/bank/submit")
	@ConfigurableDatasource
	public BankSwiftCodeMapperResponse bankReturnFormSubmit(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid BankSubmitFormRequest request) throws RollbackException {

		if ("1".equals(jwtFlag) && null != request.getCustomerId() && Constants.IS_JWT_TOKEN_ENABLE) {

			salesOrderServiceV2.authenticateOrderCheck(httpRequestHeaders, request.getCustomerId());
		}
		return salesOrderServiceV3.submitBankReturnRequest(httpRequestHeaders, request);
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/braze/wallet/add")
	@ConfigurableDatasource
	public AddStoreCreditResponse brazeWebHookForWalletUpdate(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody AddStoreCreditRequest request,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		LOGGER.info("inside oms controller, auth/oms/braze/wallet/add");

		if (Constants.orderCredentials.isExternalAuthEnable()) {
			if (configService.checkAuthorization(null, authorizationToken)) {
				return salesOrderServiceV2.brazeWalletUpdate(httpRequestHeaders, request);
			} else {
				throw new ForbiddenException();
			}
		} else {
			return salesOrderServiceV2.brazeWalletUpdate(httpRequestHeaders, request);
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@GetMapping("oms/braze/attribute/stylicredit")
	@ConfigurableDatasource
	public AddStoreCreditResponse brazeCustomAttributePush(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		LOGGER.info("inside oms controller, auth/oms/braze/attribute/stylicredit");

		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (configService.checkAuthorization(authorizationToken, null)) {
				return salesOrderServiceV2.brazeAttributePush(httpRequestHeaders);
			} else {
				throw new ForbiddenException();
			}
		} else {
			return salesOrderServiceV2.brazeAttributePush(httpRequestHeaders);
		}
	}

	/**
	 *
	 * @param request            AccountDeletionEligibleRequest
	 * @param authorizationToken String
	 * @return AccountDeletionEligibleResponse
	 *
	 *         Called internally from customer-service to check open
	 *         orders/rmas/stylicredits
	 */
	@PostMapping("delete/eligible")
	@ConfigurableDatasource
	public AccountDeletionEligibleResponse checkAccountDeletionEligibility(
			@RequestBody @Valid AccountDeletionEligibleRequest request,
			@RequestHeader(value = "authorization-token", required = true) String authorizationToken) {

		LOGGER.info("inside oms controller, delete/eligible");

		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (configService.checkAuthorization(authorizationToken, null)) {
				return salesOrderCustomerService.checkAccountDeletionEligiblity(request);
			} else {
				throw new ForbiddenException();
			}
		} else {
			return salesOrderCustomerService.checkAccountDeletionEligiblity(request);
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/order-unhold")
	@ConfigurableDatasource
	public OmsOrderresponsedto orderunhold(@RequestHeader Map<String, String> httpRequestHeaders,
			HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
		OmsOrderresponsedto omsOrderresponsedto;
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
				throw new ForbiddenException();
			} else {
				omsOrderresponsedto = salesOrderServiceV3.orderWmsUnhold();
			}
		} else {
			omsOrderresponsedto = salesOrderServiceV3.orderWmsUnhold();
		}
		return omsOrderresponsedto;
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping(value = "oms/payfort/captureDropOff")
	public void sendSMSAndMailForCaptureDropOff(@RequestHeader Map<String, String> httpRequestHeadrs,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		String directoryName = "capture_dropoff";
		File directory = new File(directoryName);
		if (!directory.exists()) {
			directory.mkdir();
		}
		String toEmail = Constants.orderCredentials.getOrderDetails().getEmail();
		List<SalesOrder> orderList = salesOrderRepository.findAuthorizationCaptureDropOffOrderList();
		LOGGER.info("number of order which have capture drop off: " + orderList.size());
		String fileName = salesOrderServiceV3.getFileForCaptureDropoffMailProcessing(directoryName, orderList);
		if (CollectionUtils.isNotEmpty(orderList)) {
			String subject = "Payfort capture dropoff " + env;
			String body = "Hi,\n" + "Please find the failure report for Styli payfort autorization capture" + ".\n"
					+ "Thanks";
			Response response = emailService.sendTextWithAttachment(toEmail, subject, body, fileName);
			if (ObjectUtils.isEmpty(response)) {
				LOGGER.info("Re-trying Error email: " + toEmail);
				emailService.sendTextWithAttachment(toEmail, subject, body, fileName);
			}
			try {
				salesOrderServiceV3.deleteDirectory(new File(directoryName));
			} catch (IOException e) {
				LOGGER.error("Error in preparing email for authorization reports. Error: ", e);
			}
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("update/order-cancel")
	@ConfigurableDatasource

	public OmsOrderresponsedto updateOrderCancel(@RequestHeader Map<String, String> httpRequestHeaders,
			HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		OmsOrderresponsedto omsOrderresponsedto;
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
				throw new ForbiddenException();
			} else {
				omsOrderresponsedto = salesOrderServiceV3.updateWmsOrderCancel();
			}
		} else {
			omsOrderresponsedto = salesOrderServiceV3.updateWmsOrderCancel();
		}
		return omsOrderresponsedto;
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/rma/pushReturnCancelToWms")
	@ConfigurableDatasource
	public OmsOrderresponsedto pushReturnCancelToWms(@RequestHeader Map<String, String> httpRequestHeaders,
			HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken,
			@RequestBody WMSReturnCancelRequest payload) {

		OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();

		if (Constants.orderCredentials.isInternalAuthEnable()
				&& !configService.checkAuthorizationInternal(authorizationToken)) {
			LOGGER.info("You're not authenticated to make return cancel request.");
			throw new ForbiddenException();
		}
		try {
			rmaUtil.pushReturnCancelToWms(payload);
			omsOrderresponsedto.setStatus(true);
			omsOrderresponsedto.setStatusCode("200");
			omsOrderresponsedto.setStatusMsg("pushed successfully");
		} catch (Exception e) {
			LOGGER.error("Exception during return cancel push to OMS for return order: " + payload.getReturnOrderCode(), e);
			omsOrderresponsedto.setStatus(false);
			omsOrderresponsedto.setStatusCode("500");
			omsOrderresponsedto.setStatusMsg("An error occurred while processing the request.");
		}
		return omsOrderresponsedto;
	}
	
	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("zatca/invoice/create")
	@ConfigurableDatasource
	public void createShipmentZatca(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid List<String> requestList,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
	
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
				throw new ForbiddenException();
			} else {
				try {
					zatcaServiceImpl.syncBulk(requestList, "INVOICE");
				} catch (Exception e) {
					LOGGER.error("createShipmentZatca Exception!" + e.getMessage());
				}
			}
		} else {
			try {
				zatcaServiceImpl.syncBulk(requestList, "INVOICE");
			} catch (Exception e) {
				LOGGER.error("createShipmentZatca Exception!" + e.getMessage());
			}
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("zatca/creditnote/create")
	@ConfigurableDatasource
	public void create(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody @Valid List<String> requestList,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
				throw new ForbiddenException();
			} else {
				try {
					zatcaServiceImpl.syncBulk(requestList, "CREDIT_MEMO");
				} catch (Exception e) {
					LOGGER.error("createShipmentZatca Exception!" + e.getMessage());
				}
			}
		} else {
			try {
				zatcaServiceImpl.syncBulk(requestList, "CREDIT_MEMO");
			} catch (Exception e) {
				LOGGER.error("createShipmentZatca Exception!" + e.getMessage());
			}
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("zatca/invoice/status-check")
	@ConfigurableDatasource
	public void syncZatcaInvoiceStatus(@RequestHeader Map<String, String> httpRequestHeaders,
			HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
				throw new ForbiddenException();
			} else {
				zatcaServiceImpl.checkStatusInvoice();
			}
		} else {
			zatcaServiceImpl.checkStatusInvoice();
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("zatca/creditmemo/status-check")
	@ConfigurableDatasource
	public void syncZatcaCreditMemoStatus(@RequestHeader Map<String, String> httpRequestHeaders,
			HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
				throw new ForbiddenException();
			} else {
				zatcaServiceImpl.checkStatusCreditMemo();
			}
		} else {
			zatcaServiceImpl.checkStatusCreditMemo();
		}
	}
	
	@PostMapping("/tracking")
	public Object authenticateTracking(@RequestBody TrackingRequest trackingRequest) {
		OrderTrackingResponseBuilder orderTrackingResponse = OrderTrackingResponse.builder();
		String encryptedAWB = trackingRequest.getWaybill();
		String incrementId = trackingRequest.getIncrement_id();

		// Scenario 1: Only encrypted AWB is provided
		if (Objects.nonNull(encryptedAWB) && Objects.isNull(incrementId)) {
			String decryptedAWB = decryptAWB(encryptedAWB);
			if (decryptedAWB != null) {
				return salesOrderServiceV3.getTrackingData(decryptedAWB);
			} else {
				return orderTrackingResponse.status("false").statusCode("400").statusMsg("Invalid waybill number").build();
			}
		}

		// Scenario 2: Only increment_id is provided
		if (Objects.isNull(encryptedAWB) && Objects.nonNull(incrementId)) {
			// Only work for global orders (G1), not local orders (L1)
			if (!incrementId.endsWith("-G1")) {
				return orderTrackingResponse.status("false").statusCode("400").statusMsg("Tracking by increment_id is only supported for global orders (G1)").build();
			}
			
			org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse incrementIdResponse = 
					salesOrderServiceV3.getTrackingDataByIncrementId(incrementId);
			if (incrementIdResponse != null) {
				// Convert to AWB-like structure with scans
				return buildAWBLikeResponseFromIncrementId(incrementIdResponse, incrementId);
			} else {
				return orderTrackingResponse.status("false").statusCode("404").statusMsg("Order not found for increment_id: " + incrementId).build();
			}
		}

		// Scenario 3: Both increment_id and encrypted AWB are provided
		if (Objects.nonNull(encryptedAWB) && Objects.nonNull(incrementId)) {
			// Execute the existing AWB logic
			String decryptedAWB = decryptAWB(encryptedAWB);
			Object awbResponse = null;
			if (decryptedAWB != null) {
				awbResponse = salesOrderServiceV3.getTrackingData(decryptedAWB);
			}

			// Also fetch the status using the increment_id logic (only for global orders)
			org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse incrementIdResponse = null;
			if (incrementId.endsWith("-G1")) {
				incrementIdResponse = salesOrderServiceV3.getTrackingDataByIncrementId(incrementId);
			}

			// Combine (club) both responses into a single consolidated response
			Map<String, Object> combinedResponse = new HashMap<>();
			
			if (awbResponse != null && incrementIdResponse != null) {
				// Merge orderTracking statusHistory into awbTracking scans
				try {
					ObjectMapper mapper = new ObjectMapper();
					Map<String, Object> awbResponseMap = mapper.convertValue(awbResponse, new TypeReference<Map<String, Object>>() {});
					
					// Navigate to response.scans
					if (awbResponseMap.containsKey("response")) {
						@SuppressWarnings("unchecked")
						Map<String, Object> responseMap = (Map<String, Object>) awbResponseMap.get("response");
						
						if (responseMap.containsKey("scans")) {
							@SuppressWarnings("unchecked")
							List<Map<String, Object>> scans = (List<Map<String, Object>>) responseMap.get("scans");
							
							// Sort external scans by ordering_index DESCENDING (latest first)
							scans.sort((a, b) -> {
								Number idxA = (Number) a.get("ordering_index");
								Number idxB = (Number) b.get("ordering_index");
								if (idxA == null) return 1;
								if (idxB == null) return -1;
								return Integer.compare(idxB.intValue(), idxA.intValue()); // descending
							});
							
							// Remove external scans with stylipost_status_code = 1 (Order Placed) 
							// and scans with all null values (empty status) since we have internal statuses
							scans.removeIf(scan -> {
								Number statusCode = (Number) scan.get("stylipost_status_code");
								// Remove if stylipost_status_code is 1 (Order Placed)
								if (statusCode != null && statusCode.intValue() == 1) {
									return true;
								}
								// Remove if all key fields are null (empty scan)
								Object statusDesc = scan.get("stylipost_status_description");
								Object statusBucket = scan.get("stylipost_status_bucket");
								return statusCode == null && statusDesc == null && statusBucket == null;
							});
							
							// Reassign ordering_index after sorting and filtering external scans
							int newIndex = 1;
							for (Map<String, Object> scan : scans) {
								scan.put("ordering_index", newIndex++);
							}
							
							// Convert orderTracking statusHistory to scan format and add to scans
							if (incrementIdResponse.getStatusHistory() != null) {
								int maxOrderingIndex = scans.size(); // Use scans size as max since we reassigned indexes
								
								// Add orderTracking statusHistory items as scans with is_internal flag
								// Reverse the list so latest status appears first
								List<org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem> reversedHistory = 
									new ArrayList<>(incrementIdResponse.getStatusHistory());
								Collections.reverse(reversedHistory);
								
								int internalOrderingIndex = maxOrderingIndex + 1;
								for (org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem statusItem : reversedHistory) {
									Map<String, Object> internalScan = new HashMap<>();
									internalScan.put("remark", statusItem.getNote());
									// Format timestamp to ISO format matching the scan structure
									if (statusItem.getTimestamp() != null) {
										java.time.Instant instant = statusItem.getTimestamp().toInstant();
										internalScan.put("timestamp", instant.toString());
									} else {
										internalScan.put("timestamp", null);
									}
									internalScan.put("ordering_index", internalOrderingIndex++);
									internalScan.put("is_internal", true);
									internalScan.put("status", statusItem.getStatus());
									// Set other fields to null to match the scan structure
									internalScan.put("stylipost_bucket_description", null);
									internalScan.put("stylipost_status_description", null);
									internalScan.put("stylipost_status_code", null);
									internalScan.put("stylipost_status_bucket", null);
									internalScan.put("notification_event_id", null);
									internalScan.put("cp_status", null);
									internalScan.put("failure_status", null);
									internalScan.put("location", null);
									internalScan.put("is_mapped_status", false);
									
									scans.add(internalScan);
								}
								
								// Sort scans by ordering_index (ascending)
								scans.sort((a, b) -> {
									Number idxA = (Number) a.get("ordering_index");
									Number idxB = (Number) b.get("ordering_index");
									if (idxA == null && idxB == null) return 0;
									if (idxA == null) return 1;
									if (idxB == null) return -1;
									return Integer.compare(idxA.intValue(), idxB.intValue());
								});
							}
							
							// Add is_internal flag to response level
							responseMap.put("has_internal_tracking", true);
						}
					}
					
					combinedResponse.put("awbTracking", awbResponseMap);
				} catch (Exception e) {
					LOGGER.error("Error merging orderTracking into awbTracking scans: " + e.getMessage(), e);
					// Fallback to separate responses if merge fails
					combinedResponse.put("awbTracking", awbResponse);
				}
			} else {
				if (awbResponse != null) {
					combinedResponse.put("awbTracking", awbResponse);
				}
			}
			
			// Remove orderTracking structure - only return awbTracking with merged scans
			if (combinedResponse.isEmpty()) {
				return orderTrackingResponse.status("false").statusCode("400").statusMsg("No tracking data found").build();
			}

			return combinedResponse;
		}

		// Neither parameter provided
		return orderTrackingResponse.status("false").statusCode("400").statusMsg("Either waybill or increment_id must be provided").build();
	}


	/**
	 * Builds AWB-like response structure from incrementId tracking data
	 * Only for global orders (G1)
	 */
	private Map<String, Object> buildAWBLikeResponseFromIncrementId(
			org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse incrementIdResponse,
			String incrementId) {
		
		Map<String, Object> response = new HashMap<>();
		response.put("status", true);
		response.put("statusCode", 200);
		response.put("statusMsg", "success");
		
		Map<String, Object> responseData = new HashMap<>();
		responseData.put("reference_number", incrementId);
		
		// Convert statusHistory to scans format
		List<Map<String, Object>> scans = new ArrayList<>();
		if (incrementIdResponse.getStatusHistory() != null) {
			int orderingIndex = 1;
			
			// Reverse the list so latest status appears first
			List<org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem> reversedHistory = 
				new ArrayList<>(incrementIdResponse.getStatusHistory());
			Collections.reverse(reversedHistory);
			
			for (org.styli.services.order.pojo.response.Order.IncrementIdTrackingResponse.StatusHistoryItem statusItem : reversedHistory) {
				Map<String, Object> scan = new HashMap<>();
				scan.put("remark", statusItem.getNote());
				// Format timestamp to ISO format
				if (statusItem.getTimestamp() != null) {
					java.time.Instant instant = statusItem.getTimestamp().toInstant();
					scan.put("timestamp", instant.toString());
				} else {
					scan.put("timestamp", null);
				}
				scan.put("ordering_index", orderingIndex++);
				scan.put("is_internal", true);
				scan.put("status", statusItem.getStatus());
				// Set other fields to null to match the scan structure
				scan.put("stylipost_bucket_description", null);
				scan.put("stylipost_status_description", null);
				scan.put("stylipost_status_code", null);
				scan.put("stylipost_status_bucket", null);
				scan.put("notification_event_id", null);
				scan.put("cp_status", null);
				scan.put("failure_status", null);
				scan.put("location", null);
				scan.put("is_mapped_status", false);
				
				scans.add(scan);
			}
		}
		
		responseData.put("scans", scans);
		responseData.put("has_internal_tracking", true);
		
		// Set latest_status to the last status in history
		if (!scans.isEmpty()) {
			Map<String, Object> latestStatus = new HashMap<>(scans.get(scans.size() - 1));
			responseData.put("latest_status", latestStatus);
		}
		
		response.put("response", responseData);
		
		return response;
	}

	private String decryptAWB(String encryptedAWB) {
		try {
			final String SECRET_KEY = Constants.orderCredentials.getOrderDetails().getSecretkey();
			final String salt = Constants.orderCredentials.getOrderDetails().getSalt();
			int iterations = 10000;
			int keyLength = 128;

			KeySpec spec = new PBEKeySpec(SECRET_KEY.toCharArray(), salt.getBytes(), iterations, keyLength);
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			byte[] keyBytes = factory.generateSecret(spec).getEncoded();

			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");
			cipher.init(Cipher.DECRYPT_MODE, secretKey);

			byte[] encryptedBytes = Base64.getDecoder().decode(encryptedAWB);
			byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
			String decryptedAWB = new String(decryptedBytes);

			return decryptedAWB;
		} catch (Exception e) {
			LOGGER.info("Tracking data: Exception occurred in decrypting AWB ", e);
			return null;
		}
	}
	
	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/order-hold-false")
	@ConfigurableDatasource
	public OmsOrderresponsedto orderHoldFalse(@RequestHeader Map<String, String> httpRequestHeaders,
			HttpServletRequest req,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
		OmsOrderresponsedto omsOrderresponsedto;
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (!configService.checkAuthorizationInternal(authorizationToken)) {
				LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
				throw new ForbiddenException();
			} else {
				omsOrderresponsedto = salesOrderServiceV3.orderWmsHoldFalse();
			}
		} else {
			omsOrderresponsedto = salesOrderServiceV3.orderWmsHoldFalse();
		}
		return omsOrderresponsedto;
	}
	
	@PostMapping("/tracking/dawb")
	public Object getTrackingDetails(@RequestBody TrackingRequest trackingRequest) {
		LOGGER.info("Tracking data : Inside /tracking/dawb function ");
		OrderTrackingResponseBuilder orderTrackingResponse = OrderTrackingResponse.builder();
		String awbNumber = trackingRequest.getWaybill();
		if (Objects.nonNull(awbNumber)) {
			return salesOrderServiceV3.getTrackingData(awbNumber);
		} else {
			return orderTrackingResponse.status("false").statusCode("400").statusMsg("Waybill Number missing").build();
		}
	}

	@ApiImplicitParams({
	    @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true)
	})
	@PostMapping("/refund/alerts")
	@ConfigurableDatasource
	public OmsOrderresponsedto orderRefundAlerts(
	        @RequestHeader Map<String, String> httpRequestHeaders,
	        HttpServletRequest req,
	        @RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

	    if (Constants.orderCredentials.isInternalAuthEnable() && 
	        !configService.checkAuthorizationInternal(authorizationToken)) {
	        LOGGER.info(Constants.UNAUTHENTICATED_REQUEST_MSG);
	        throw new ForbiddenException();
	    }

	    return salesOrderServiceV3.findOrdersNotRefunded();
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping(value = "oms/shukran/lockAndUnlock")
	@ConfigurableDatasource
	public LockAndUnlockShukranResponse lockAndUnlockShukran(@RequestBody @Valid LockAndUnlockShukranRequest lockAndUnlockShukranRequest,
															 @RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable()) {

			if (configService.checkAuthorization(authorizationToken, null)) {
				return commonService.lockAndUnlockShukran(lockAndUnlockShukranRequest);

			} else {

				throw new ForbiddenException();
			}
		} else {
			return commonService.lockAndUnlockShukran(lockAndUnlockShukranRequest);
		}

	}

	/**
	 * Endpoint to unlock Shukran points for the Canceled and failed orders.
	 *
	 * This method handles HTTP POST requests to the /shukran/unlock endpoint.
	 * It invokes the {@code unlockShukranPoints} method from {@code commonService} to
	 * process and unlock Shukran loyalty points, making them available for use.
	 *
	 * @return a response containing the status and details
	 *         of the unlocking operation, such as success Or failure status
	 */
	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping(value = "oms/shukran/unlock")
	@ConfigurableDatasource
	public LockAndUnlockShukranResponse unlockShukranPoints(@RequestHeader(value = "authorization-token", required = false) String authorizationToken){
		if (Constants.orderCredentials.isInternalAuthEnable()) {

			if (configService.checkAuthorization(authorizationToken, null)) {
				return commonService.unlockShukranPoints();
			} else {
				throw new ForbiddenException();
			}
		} else {
			return commonService.unlockShukranPoints();
		}

	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@ConfigurableDatasource
	@GetMapping("oms/findCustomerPoints/{profileId}")
	public BigDecimal findCustomerShukranPoints(@PathVariable @NotNull String profileId, @RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
		if (Constants.orderCredentials.isInternalAuthEnable()) {
			if (configService.checkAuthorization(authorizationToken, null)) {
				return commonService.customerShukranBalance(profileId);
			} else {
				throw new ForbiddenException();
			}
		} else {
			return commonService.customerShukranBalance(profileId);
		}
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/seller/process-orders")
	@ConfigurableDatasource
	public OmsOrderresponsedto processApparelOrders(@RequestHeader Map<String, String> httpRequestHeaders, HttpServletRequest req,
													@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

		if (Constants.orderCredentials.isInternalAuthEnable()
				&& !configService.checkAuthorizationInternal(authorizationToken)) {
			LOGGER.info("[OrderController] You're not authenticated to make order push request.");
			throw new ForbiddenException();
		}
		OmsOrderresponsedto omsOrderresponsedto = new OmsOrderresponsedto();
		//Get this query checked
		List<SalesOrder> orderList = salesOrderServiceV3.orderpushTowmsForApparel();
		for (SalesOrder order : orderList) {
			LOGGER.info("[OrderController] Order increment ID for WMS status update :: "+ order.getIncrementId());
			try {
				List<SplitSellerOrder> sellerOrders = orderpushHelper.orderpushToApparelWms(order);
                if (CollectionUtils.isNotEmpty(sellerOrders)) {
                    for (SplitSellerOrder splitSellerOrder : sellerOrders) {
                        if (splitSellerOrder.getSplitOrder() == null || splitSellerOrder.getSplitOrder().getStoreId() == null) {
                            LOGGER.info("Process seller orders splitSellerOrder with sales order increment ID for WMS status update :: " + splitSellerOrder.getIncrementId());
                            try {
                                salesOrderServiceV3.getSalesOrderForSellerOrder(splitSellerOrder);
                                orderpushHelper.orderpushTowmsv3(Arrays.asList(splitSellerOrder));
                            } catch (Exception e) {
                                LOGGER.error("Process seller orders exception during push to oms: for splitSellerOrder :" + splitSellerOrder.getIncrementId(), e);
                            }
                        } else {
                            LOGGER.info("Process seller orders splitSellerOrder with split order increment ID for WMS status update :: " + splitSellerOrder.getIncrementId());
                            try {
                                orderpushHelper.orderpushTowmsv3(Arrays.asList(splitSellerOrder));
                            } catch (Exception e) {
                                LOGGER.error("Process seller orders exception during push to oms: for splitSellerOrder :" + splitSellerOrder.getIncrementId(), e);
                            }
                        }
                    }
                }
			} catch (Exception e) {
				LOGGER.error("[OrderController] exception during push to oms: for order :" + order.getIncrementId(), e);
			}
		}
		omsOrderresponsedto.setStatus(true);
		omsOrderresponsedto.setStatusCode("200");
		omsOrderresponsedto.setStatusMsg("pushed successfully");
		return omsOrderresponsedto;
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping("oms/dummy-return-shipment")
	@ConfigurableDatasource
	public DummyReturnShipmentResponse dummyReturnShipmentProcessing(@RequestHeader Map<String, String> httpRequestHeaders, HttpServletRequest req,
															@RequestHeader(value = "authorization-token", required = false) String authorizationToken,
															@RequestBody DummyReturnShipmentRequest request) {		
		DummyReturnShipmentResponse response = new DummyReturnShipmentResponse();
		
		try {
			// Validate request
			if (request == null || request.getReturnIncrementIds() == null || request.getReturnIncrementIds().isEmpty()) {
				response.setStatus(false);
				response.setStatusCode("400");
				response.setStatusMsg("Return increment IDs are required");
				return response;
			}
			
			List<String> returnIncrementIds = request.getReturnIncrementIds();
			
			String csvContent = salesOrderServiceV3.processDummyReturnShipmentWithCsvContent(returnIncrementIds);
			
			String csvFileName = "dummy_return_shipments_" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")
					.format(java.time.LocalDateTime.now()) + ".csv";
			
			response.setStatus(true);
			response.setStatusCode("200");
			response.setStatusMsg("Dummy return shipments processed successfully");
			response.setCsvFileName(csvFileName);
			response.setCsvFileContent(csvContent);
			
		} catch (Exception e) {
			LOGGER.error("[OrderController] Exception in dummy return shipment processing", e);
			response.setStatus(false);
			response.setStatusCode("500");
			response.setStatusMsg("Error processing dummy return shipments: " + e.getMessage());
		}
		
		return response;
	}

	@ApiImplicitParams({
			@ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
	@PostMapping(value = "dispatch/update", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ConfigurableDatasource
	public ResponseEntity<?> updateDispatchCancelAllowed(@RequestHeader Map<String, String> httpRequestHeaders,
			@RequestBody String xmlPayload,
			@RequestHeader(value = "authorization-token", required = false) String authorizationToken) {
		
		Map<String, Object> response = salesOrderService.processDispatchUpdate(xmlPayload, authorizationToken);
		
		String statusCode = (String) response.get("statusCode");
		if ("200".equals(statusCode)) {
			return ResponseEntity.ok(response);
		} else if ("400".equals(statusCode)) {
			return ResponseEntity.badRequest().body(response);
		} else {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

}


