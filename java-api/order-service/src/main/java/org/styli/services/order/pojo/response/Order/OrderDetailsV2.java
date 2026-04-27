package org.styli.services.order.pojo.response.Order;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderDetailsV2 extends OrderResponse{
    private Payments payments;
    private Integer totalSplitOrderCount;
    private String globalShippingAmount;
    private List<SplitOrderDTO> splitOrders;
}
