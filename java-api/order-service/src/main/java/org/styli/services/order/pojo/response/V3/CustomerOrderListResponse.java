package org.styli.services.order.pojo.response.V3;

import lombok.Data;

import java.util.List;

@Data
public class CustomerOrderListResponse {
    private List<OrderResponseV3> orders;
}
