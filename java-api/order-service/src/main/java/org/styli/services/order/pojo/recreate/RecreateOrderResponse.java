package org.styli.services.order.pojo.recreate;

import lombok.Data;
import org.styli.services.order.pojo.request.Order.OmsProduct;
import org.styli.services.order.pojo.response.OrderTotal;

import java.util.List;

@Data
public class RecreateOrderResponse {

    private Integer orderId;
    private OrderTotal totals;
    private List<OmsProduct> products;
}
