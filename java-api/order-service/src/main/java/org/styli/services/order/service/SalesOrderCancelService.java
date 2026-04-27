package org.styli.services.order.service;

import org.springframework.stereotype.Service;
import org.styli.services.order.pojo.cancel.CancelOrderInitResponseDTO;
import org.styli.services.order.pojo.cancel.CancelOrderRequest;
import org.styli.services.order.pojo.response.Order.OrderResponseDTO;

/**
 * @author Umesh, 14/05/2020
 * @project product-service
 */

@Service
public interface SalesOrderCancelService {

    OrderResponseDTO cancelOrder(CancelOrderRequest request);

    OrderResponseDTO cancelOrderV2(CancelOrderRequest request);

    CancelOrderInitResponseDTO cancelOrderInit(CancelOrderRequest request);
}
