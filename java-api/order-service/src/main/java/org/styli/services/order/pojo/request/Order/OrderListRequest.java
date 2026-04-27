package org.styli.services.order.pojo.request.Order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author Umesh, 12/05/2020
 * @project product-service
 */

@Data
public class OrderListRequest {

    private Integer customerId;

    public Integer storeId;

    @Min(1)
    private Integer pageSize = 5;

    @Min(0)
    private Integer offSet = 0;
    
    private String customerEmail;
    
    private boolean useArchive;
    
    private Integer websiteId;

}
