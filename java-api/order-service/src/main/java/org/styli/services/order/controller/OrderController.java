package org.styli.services.order.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.styli.services.order.config.aop.ConfigurableDatasource;
import org.styli.services.order.converter.OrderEntityConverter;
import org.styli.services.order.converter.OrderEntityConverterV1;
import org.styli.services.order.db.product.exception.ForbiddenException;
import org.styli.services.order.helper.MulinHelper;
import org.styli.services.order.helper.OrderpushHelper;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.pojo.ErrorType;
import org.styli.services.order.pojo.QuoteUpdateDTO;
import org.styli.services.order.pojo.mulin.ProductResponseBody;
import org.styli.services.order.pojo.order.CreateReplicaQuoteV4Request;
import org.styli.services.order.pojo.order.OrderEmailRequest;
import org.styli.services.order.pojo.order.UpdateOrderResponseDTO;
import org.styli.services.order.pojo.request.*;
import org.styli.services.order.pojo.request.Order.*;
import org.styli.services.order.pojo.response.BankCouponsresponse;
import org.styli.services.order.pojo.response.Coupon.ProductPromotionsDTO;
import org.styli.services.order.pojo.response.V3.CustomerOrdersResponseV2DTO;
import org.styli.services.order.pojo.response.CustomerStoreCreditResponse;
import org.styli.services.order.pojo.response.CustomerStoreCreditResponseList;
import org.styli.services.order.pojo.response.Order.*;
import org.styli.services.order.service.*;
import org.styli.services.order.utility.Constants;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController

@RequestMapping("/rest/order/auth/")
@Api(value = "/rest/order/auth/", produces = "application/json")
@JsonIgnoreProperties(ignoreUnknown = true)

public class OrderController {

    private static final Log LOGGER = LogFactory.getLog(OrderController.class);

    @Autowired
    SalesOrderService salesOrderService;


    @Autowired
    SalesOrderRetryService salesOrderRetryService;

    @Autowired
    SalesOrderRMAService salesOrderRMAService;

    @Autowired
    private OrderEntityConverter orderEntityConverter;

    @Autowired
    SalesOrderServiceV2 salesOrderServiceV2;

    @Autowired
    SalesOrderServiceV3 salesOrderServiceV3;

    @Value("${order.jwt.flag}")
    String jwtFlag;

    @Autowired
    MulinHelper mulinHelper;

    @Autowired
    @Qualifier("withoutEureka")
    private RestTemplate restTemplate;

    @Autowired
    OrderEntityConverterV1 orderEntityConverterV1;

    @Autowired
    ConfigService configService;

    @Autowired
    SalesOrderCustomerService salesOrderCustomerService;

    @Autowired
	OrderpushHelper orderpushHelper;

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("view")
    @ConfigurableDatasource
    public OrderResponseDTO orders(@RequestHeader Map<String, String> requestHeader,
                                   @RequestBody @Valid OrderViewRequest request, @RequestHeader(value = "x-client-version", required = false) String xClientVersion ) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(requestHeader, request.getCustomerId());
        }
        return salesOrderService.fetchOrderById(requestHeader, request, xClientVersion);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("v2/view")
    @ConfigurableDatasource
    public OrderResponseV2 ordersV2(@RequestHeader Map<String, String> requestHeader,
                                   @RequestBody @Valid OrderViewRequest request, @RequestHeader(value = "x-client-version", required = false) String xClientVersion ) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(requestHeader, request.getCustomerId());
        }
        return salesOrderService.fetchOrderByIdV2(requestHeader, request, xClientVersion);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @GetMapping("orderid/{orderId}/storeid/{storeId}")
    public OrderResponseDTO getOrder(@RequestHeader Map<String, String> requestHeader, @PathVariable Integer orderId,
                                     @PathVariable Integer storeId) {

        OrderResponseDTO orderResponseDTO = new OrderResponseDTO();
        ErrorType error = new ErrorType();

        SalesOrder order = salesOrderService.getOrderByEntityId(orderId, storeId);

        ObjectMapper mapper = new ObjectMapper();

        if (order != null) {

            if("1".equals(jwtFlag) && null !=order.getCustomerId() && Constants.IS_JWT_TOKEN_ENABLE
                    && MapUtils.isNotEmpty(requestHeader)) {

                salesOrderServiceV2.authenticateOrderCheck(requestHeader, order.getCustomerId());
            }
            orderResponseDTO.setStatus(true);
            orderResponseDTO.setStatusCode("200");
            orderResponseDTO.setStatusMsg("Order Fetched successfully!");
            Map<String, ProductResponseBody> productsFromMulin =
                    mulinHelper.getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
            OrderResponse orderResponseBody = orderEntityConverter.convertOrder(order, true, mapper, storeId,
                    productsFromMulin, "", false);
            orderResponseDTO.setResponse(orderResponseBody);
        } else {
            orderResponseDTO.setStatus(false);
            orderResponseDTO.setStatusCode("201");
            orderResponseDTO.setStatusMsg("Error: Order was not found!");

            error.setErrorCode("201");
            error.setErrorMessage("Order was not found!");

            orderResponseDTO.setError(error);
        }
        return orderResponseDTO;
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("email")
    public UpdateOrderResponseDTO sendEmail(@RequestBody @Valid OrderEmailRequest request) {
        return salesOrderService.sendEmailForOrder(request);
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("status")
    public UpdateOrderResponseDTO updateOrder(@RequestBody @Valid UpdateOrderRequest request) {

        return salesOrderService.updateOrder(request);

    }


    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("list")
    // @CrossOrigin(origins = "*")
    @ConfigurableDatasource
    public CustomerOrdersResponseDTO orderderListAll(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid OrderListRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
        }
        return salesOrderService.getCustomerOrders(request);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("list/v2")
    // @CrossOrigin(origins = "*")
    @ConfigurableDatasource
    public CustomerOrdersResponseDTO orderderListAllV2(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid OrderListRequestV2 request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
        }
        return salesOrderService.getCustomerOrdersV2(request);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("list/v3")
    // @CrossOrigin(origins = "*")
    @ConfigurableDatasource
    public CustomerOrdersResponseV2DTO orderderListAllV3(@RequestHeader Map<String, String> httpRequestHeadrs, @RequestBody @Valid OrderListRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
        }
        return salesOrderService.getCustomerOrdersV3(request);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("unpaid/list")
    public CustomerOrdersResponseDTO orderListFailed(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid OrderListRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
        }
        return salesOrderService.getCustomerOrdersFailed(request);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("count")
    @ConfigurableDatasource
    public CustomerOrdersCountResponseDTO getOrderCountAll(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid OrderListRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
        }
        return salesOrderService.getCustomerOrdersCountAll(request, true);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("total/count")
    @ConfigurableDatasource
    // @CrossOrigin(origins = "*")
    public CustomerOrdersCountResponseDTO getAllOrderCount(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid OrderListRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
        }
        return salesOrderService.getCustomerOrdersCountAll(request, false);

    }

    @PostMapping("storecredit/history")
    // @CrossOrigin(origins = "*")
    public CreditHistoryResponse stireCreditHistory(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid OrderStoreCreditRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
        }
        return salesOrderService.getStoreCreditHistory(request);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("return/list")
    @ConfigurableDatasource
    public RMAOrderResponseDTO orderListReturns(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid OrderListRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
        }
        return salesOrderRMAService.getcustomerReturns(request);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("v2/return/list")
    @ConfigurableDatasource
    public RMAOrderResponseDTO orderListReturnsV2(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid ReturnListRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
        }
        return salesOrderRMAService.getCustomerReturnsV2(request);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("return/item/view")
    @ConfigurableDatasource
    public ReturnItemViewResponseDTO orderReturnView(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid ReturnItemViewRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, request.getCustomerId());
        }
        return salesOrderRMAService.getCustomerReturnItemView(request);

    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("return/count")
    @ConfigurableDatasource
    public RMAOrderResponseDTO orderCountReturns(@RequestHeader Map<String, String> httpRequestHeaders,
                                                 @RequestBody @Valid OrderListRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeaders, request.getCustomerId());
        }
        return salesOrderRMAService.getCustomerReturnsCount(request);

    }

    @PostMapping("coupons/list")
    public ProductPromotionsDTO getAllCoupons(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid GetPromosRequest request) {

        return salesOrderService.getAllcouponList(request);

    }


    @PostMapping("v2/storecredit")
    public CustomerStoreCreditResponse getStoreCreditDetails(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody StoreCreditRequest storeRequest) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(httpRequestHeadrs, storeRequest.getCustomerId());
        }

        return salesOrderService.getCustomerStoreCredit(storeRequest,httpRequestHeadrs);
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("v2/replica")
    public QuoteUpdateDTO createQuoteReplica(@RequestBody @Valid CreateReplicaQuoteV4Request request,
                                             @RequestHeader("Token") String tokenHeader, @RequestHeader(value = "device-id", required = false) String deviceId) {

        return salesOrderServiceV2.createQuoteReplica(request, tokenHeader, deviceId);


    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = false) })
    @PostMapping("v2/retry-payment/replica")
    public QuoteUpdateDTO createRetryPaymentReplica(@RequestBody @Valid CreateReplicaQuoteV4Request request,
                                                    @RequestHeader("Token") String tokenHeader, @RequestHeader(value= "device-id", required = false) String deviceId) {

        return salesOrderServiceV2.createRetryPaymentReplica(request, tokenHeader, deviceId);

    }

    @PostMapping("coupons/bank/list")
    public BankCouponsresponse getBankCoupons(@RequestHeader Map<String, String> httpRequestHeadrs,@RequestBody @Valid GetPromosRequest request) {

        return salesOrderService.getBankCouponsList(request,httpRequestHeadrs);

    }

    @PostMapping("v2/storecredit/list")
    public CustomerStoreCreditResponseList getStoreCreditList(@RequestHeader Map<String, String> httpRequestHeadrs,
                                                              @RequestBody StoreCreditListRequest storeRequest,
                                                              @RequestHeader(value = "authorization-token", required = false) String authorizationToken) {

        if (Constants.orderCredentials.isInternalAuthEnable()) {
            if (!configService.checkAuthorizationInternal(authorizationToken)) {
                throw new ForbiddenException();
            } else {
                salesOrderService.getCustomerStoreCreditList(storeRequest);
            }
        } else {
            salesOrderService.getCustomerStoreCreditList(storeRequest);
        }

        return salesOrderService.getCustomerStoreCreditList(storeRequest);
    }


    @PostMapping("paymentId/{paymentId}")
    public OrderResponseDTO getOrder(Map<String, String> httpRequestHeadrs, @PathVariable String paymentId) {

        OrderResponseDTO orderResponseDTO = new OrderResponseDTO();
        ErrorType error = new ErrorType();

        Integer orderId = salesOrderService.findOrderId(paymentId);

        SalesOrder order = salesOrderService.getOrderByEntityId(orderId);

        ObjectMapper mapper = new ObjectMapper();

        if (order != null) {
            orderResponseDTO.setStatus(true);
            orderResponseDTO.setStatusCode("200");
            orderResponseDTO.setStatusMsg("Order Fetched successfully!");
            Map<String, ProductResponseBody> productsFromMulin = mulinHelper
                    .getMulinProductsFromOrder(Collections.singletonList(order), restTemplate);
            OrderResponse orderResponseBody = orderEntityConverterV1.convertOrder(order, true, mapper,
                    productsFromMulin, paymentId);
            orderResponseDTO.setResponse(orderResponseBody);
        } else {
            orderResponseDTO.setStatus(false);
            orderResponseDTO.setStatusCode("201");
            orderResponseDTO.setStatusMsg("Error: Order was not found!");

            error.setErrorCode("201");
            error.setErrorMessage("Order was not found!");

            orderResponseDTO.setError(error);
        }
        return orderResponseDTO;
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "Token", value = "KEY {{TOKEN}}", paramType = "header", required = true) })
    @PostMapping("payable/view")
    @ConfigurableDatasource
    public OrderResponseDTO orderPaybleDetail(@RequestHeader Map<String, String> requestHeader,
                                              @RequestBody @Valid OrderViewRequest request) {

        if("1".equals(jwtFlag)) {
            salesOrderServiceV2.authenticateCheck(requestHeader, request.getCustomerId());
        }
        return salesOrderService.orderPaybleDetail(requestHeader, request);

    }

}