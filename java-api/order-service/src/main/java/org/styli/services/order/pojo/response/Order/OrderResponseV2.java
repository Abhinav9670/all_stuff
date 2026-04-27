package org.styli.services.order.pojo.response.Order;

import lombok.Data;

@Data
public class OrderResponseV2 {
    private boolean status;
    private String statusCode;
    private String statusMsg;
    private OrderDetailsV2 response;
    private Object responseList;
    private Object error;
    private boolean refund;
}
