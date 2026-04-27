package org.styli.services.order.pojo.request.Order;

import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Component
public class OrderId {
    Integer orderId;
    String incrementId;
    String orderStatus;
    Boolean returnStatus;
    Integer shipmentId;
    String estimatedDeliveryDate;
}
