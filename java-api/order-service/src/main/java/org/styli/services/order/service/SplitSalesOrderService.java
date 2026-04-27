package org.styli.services.order.service;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.stereotype.Service;
import org.styli.services.order.model.sales.ProxyOrder;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.model.sales.SalesOrderGrid;
import org.styli.services.order.model.sales.SplitSalesOrder;
import org.styli.services.order.pojo.PendingPaymentToFailedResponse;
import org.styli.services.order.pojo.order.OrderEmailRequest;
import org.styli.services.order.pojo.order.UpdateOrderResponseDTO;
import org.styli.services.order.pojo.request.GetPromosRequest;
import org.styli.services.order.pojo.request.Order.*;
import org.styli.services.order.pojo.request.StoreCreditListRequest;
import org.styli.services.order.pojo.request.StoreCreditRequest;
import org.styli.services.order.pojo.request.UpdateOrderRequest;
import org.styli.services.order.pojo.response.BankCouponsresponse;
import org.styli.services.order.pojo.response.CustomerStoreCreditResponse;
import org.styli.services.order.pojo.response.CustomerStoreCreditResponseList;
import org.styli.services.order.pojo.response.OmsOrderoutboundresponse;
import org.styli.services.order.pojo.response.OmsOrderupdateresponse;
import org.styli.services.order.pojo.response.Coupon.ProductPromotionsDTO;
import org.styli.services.order.pojo.response.Order.CreditHistoryResponse;
import org.styli.services.order.pojo.response.Order.CustomerOrdersCountResponseDTO;
import org.styli.services.order.pojo.response.Order.CustomerOrdersResponseDTO;
import org.styli.services.order.pojo.response.Order.OmsOrderresponsedto;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Service
public interface SplitSalesOrderService {
    public List<SplitSalesOrder> findByOrderId(Integer orderId);
}
