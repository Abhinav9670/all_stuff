package org.styli.services.order.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.styli.services.order.pojo.order.CancelRMARequest;
import org.styli.services.order.pojo.order.RMAOrderInitV2ResponseDTO;
import org.styli.services.order.pojo.order.RMAOrderV2Request;
import org.styli.services.order.pojo.order.RMAOrderV2RequestWrapper;
import org.styli.services.order.pojo.request.Order.OrderListRequest;
import org.styli.services.order.pojo.request.Order.ReturnItemViewRequest;
import org.styli.services.order.pojo.request.Order.ReturnListRequest;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;
import org.styli.services.order.pojo.response.Order.RMAOrderResponseDTO;
import org.styli.services.order.pojo.response.Order.ReturnItemViewResponseDTO;

/**
 * @author Umesh, 27/05/2020
 * @project product-service
 */

@Service
public interface SalesOrderRMAService {

    RMAOrderResponseDTO getcustomerReturns(OrderListRequest request);

    RMAOrderResponseDTO getCustomerReturnsV2(ReturnListRequest request);

    ReturnItemViewResponseDTO getCustomerReturnItemView(ReturnItemViewRequest request);

    @Deprecated
    OrderResponseDTO cancelRMAOrder(CancelRMARequest request);

    RMAOrderInitV2ResponseDTO rmaOrderVersionTwoInit(RMAOrderV2Request request, String xClientVersion);
    
    RMAOrderInitV2ResponseDTO rmaOrderVersionTwoInitWrapper(List<RMAOrderV2RequestWrapper> wrappers, String xClientVersion);
    
    RMAOrderInitV2ResponseDTO rmaOrderVersionTwoInitSingle(RMAOrderV2RequestWrapper wrapper, String xClientVersion);

    OrderResponseDTO rmaOrderVersionTwo(RMAOrderV2Request request, String xClientVersion);

    OrderResponseDTO rmaOrderVersionTwoWrapper(List<RMAOrderV2RequestWrapper> requestWrappers, String xClientVersion);
    
    OrderResponseDTO rmaOrderVersionTwoSingle(RMAOrderV2RequestWrapper wrapper, String xClientVersion);

    void dropoffCall(OrderResponseDTO resp) ;

    RMAOrderResponseDTO getCustomerReturnsCount(OrderListRequest request);
}
