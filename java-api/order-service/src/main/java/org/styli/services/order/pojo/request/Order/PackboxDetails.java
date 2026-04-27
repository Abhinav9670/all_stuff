package org.styli.services.order.pojo.request.Order;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @author Assistant
 * @project order-service
 */
@Data
public class PackboxDetails {
    
    private Double length;
    
    private Double breadth;
    
    private Double height;
    
    private Double weight;
    
    @NotNull(message = "BoxId is required")
    private Long boxId;
    
    private String boxCode;
    
    private Double volWeight;
    

    // no longer used 
    // private String boxSkuId;
    
    @Valid
    private List<SkuQuantityData> skuQuantityDataList;
}
