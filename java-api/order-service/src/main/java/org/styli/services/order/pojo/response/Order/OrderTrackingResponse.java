package org.styli.services.order.pojo.response.Order;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderTrackingResponse {
    private Object response;
    private String status;
    private String statusCode;
    private String statusMsg;
    
}

