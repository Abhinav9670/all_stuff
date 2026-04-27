package org.styli.services.order.pojo.request.Order;

import lombok.Data;
import javax.validation.constraints.NotNull;

/**
 * @author Assistant
 * @project order-service
 */
@Data
public class SkuQuantityData {
    
    @NotNull(message = "GlobalSkuId is required")
    private Long globalSkuId;
    
    @NotNull(message = "ClientSkuId is required")
    private String clientSkuId;
    
    @NotNull(message = "Count is required")
    private Integer count;
}
