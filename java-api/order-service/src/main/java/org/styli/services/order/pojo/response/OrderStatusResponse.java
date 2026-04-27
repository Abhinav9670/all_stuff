package org.styli.services.order.pojo.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Main response wrapper for order status operations
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderStatusResponse {
    
    private boolean success;
    
    private OrderStatusData data;
}
