package org.styli.services.order.service;

import org.springframework.stereotype.Service;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.pojo.PendingPaymentToFailedResponse;
import org.styli.services.order.pojo.oms.DispatchUpdateRequest;
import org.styli.services.order.pojo.order.OrderEmailRequest;
import org.styli.services.order.pojo.order.UpdateOrderResponseDTO;
import org.styli.services.order.pojo.request.GetPromosRequest;
import org.styli.services.order.pojo.request.Order.*;
import org.styli.services.order.pojo.request.StoreCreditListRequest;
import org.styli.services.order.pojo.request.StoreCreditRequest;
import org.styli.services.order.pojo.request.UpdateOrderRequest;
import org.styli.services.order.pojo.response.*;
import org.styli.services.order.pojo.response.Coupon.ProductPromotionsDTO;
import org.styli.services.order.pojo.response.Order.*;
import org.styli.services.order.pojo.response.V3.CustomerOrdersResponseV2DTO;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;
/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Service
public interface SalesOrderService {
    public SalesOrder getOrderByEntityId(Integer orderId, Integer storeId);

    public SalesOrderGrid getOrderGridByEntityId(Integer orderId, Integer storeId);

    public CustomerOrdersResponseDTO getCustomerOrders(OrderListRequest request);

	public CustomerOrdersResponseV2DTO getCustomerOrdersV3(OrderListRequest request);

    CustomerOrdersResponseDTO getCustomerOrdersFailed(OrderListRequest request);

    OrderResponseDTO fetchOrderById(Map<String, String> requestHeader, OrderViewRequest request, String xClientVersion);

	OrderResponseV2 fetchOrderByIdV2(Map<String, String> requestHeader, OrderViewRequest request, String xClientVersion);

    CustomerOrdersCountResponseDTO getCustomerOrdersCountAll(OrderListRequest request, Boolean forOrderPage);

    CreditHistoryResponse getStoreCreditHistory(@Valid OrderStoreCreditRequest request);



    UpdateOrderResponseDTO updateOrder(UpdateOrderRequest request);

    UpdateOrderResponseDTO sendEmailForOrder(OrderEmailRequest request);

    ProductPromotionsDTO getAllcouponList(GetPromosRequest storeId);

    public String getOrderIncrementId(Integer storeId);

    public CustomerStoreCreditResponse getCustomerStoreCredit(StoreCreditRequest storeRequest,Map<String, String> httpRequestHeadrs);

	 BankCouponsresponse getBankCouponsList(@Valid GetPromosRequest request,Map<String, String> httpRequestHeadrs);

	public CustomerOrdersResponseDTO getReferalOrderList(Map<String, String> httpRequestHeadrs);

	public CustomerStoreCreditResponseList getCustomerStoreCreditList(StoreCreditListRequest storeRequest);

	
	public CustomerOrdersResponseDTO getCustomerOmsOrders(OmsOrderListRequest request);

	public OmsOrderresponsedto getOmsOrderDetails(@Valid OrderViewRequest request);

	public OmsOrderresponsedto getOmsOrderShippingDetails(@Valid OrderViewRequest request);

	public OmsOrderresponsedto getOmsOrderInvoiceDetails(@Valid OrderViewRequest request);

	public OmsOrderupdateresponse omsStatusupdate(@Valid OrderupdateRequest request);

	public OmsOrderupdateresponse omsOrderaddressupdate(@Valid OrderupdateRequest request, Map<String, String> requestHeader);

	public OmsOrderoutboundresponse crerateShipment(@Valid OrderViewRequest request);

	public Integer findOrderId(String paymentId);
	
	public SalesOrder findSalesOrderByPaymentId(String paymentId);

	public SalesOrder getOrderByEntityId(Integer orderId);

	List<SalesOrder> findSalesOrdeForTabbyPayment();
	
	List<ProxyOrder> findPendingProxyOrders();
    SalesOrder findSalesOrderByIncrementId(String incrementId);
    
    List<SalesOrder> findSalesOrdeForCfPayment();
	List<SalesOrder> findByEntityId(List<Integer> incrementIds);
	
	OrderResponseDTO orderPaybleDetail(Map<String, String> requestHeader, OrderViewRequest request);

	public PendingPaymentToFailedResponse makePaymentPendingOrdersToPaymentFailed();

	public void sendBrazeNotificatonForPendingOrder();
	
	public PendingPaymentToFailedResponse makefailedNonHoldOrders();

	public CustomerOrdersResponseDTO getCustomerOrdersV2(OrderListRequestV2 request);

	public void updateDispatchCancelAllowed(DispatchUpdateRequest request);

	public Map<String, Object> processDispatchUpdate(String xmlPayload, String authorizationToken);
}
