package org.styli.services.order.pojo.request.Order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author Umesh, 12/05/2020
 * @project product-service
 */

@Data
public class OrderListRequestV2 {

    @NotNull(message = "Customer ID cannot be null")
    private Integer customerId;

    @NotNull(message = "Store ID cannot be null")
    public Integer storeId;

    @Min(value = 1, message = "Page size must be at least 1")
    private Integer pageSize = 5;

    @Min(value = 0, message = "Offset must be at least 0")
    private Integer offSet = 0;
    
    private String customerEmail;
    
    private boolean useArchive;
    
    private Integer websiteId;

}
