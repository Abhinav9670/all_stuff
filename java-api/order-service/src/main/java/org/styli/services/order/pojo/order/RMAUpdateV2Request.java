package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Umesh, 14/07/2021
 * @project product-service
 */

@Data
public class RMAUpdateV2Request implements Cloneable {

    @NotNull @Min(1)
    private Integer customerId;

    @NotNull @Min(1)
    private Integer requestId;

    @NotNull @Min(1)
    private Integer storeId;

    @NotNull @Min(1)
    private Integer status;

    List<RMAUpdateItemV2Request> items;

    private Boolean closeRma = false;
    
    List<SkuQtyChangeData> skuQtyChangeData;
    
    private String returnId;
    
    private String orderId;
    
    private String returnItemOrderCode;
    
    private Integer qty;
    
    @Override
    public Object clone() throws CloneNotSupportedException {
    	return super.clone();
    }
    
}
