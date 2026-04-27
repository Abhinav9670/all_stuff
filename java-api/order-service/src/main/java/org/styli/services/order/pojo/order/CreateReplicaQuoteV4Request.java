package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author Umesh, 19/03/2020
 * @project product-service
 */

@Data
public class CreateReplicaQuoteV4Request {

  //@NotNull

    //@Min(1)
    private Integer orderId;

    @NotNull
    @Min(1)
    private Integer storeId;
    
    private String tabbyPaymentId;
    
    private String failedPaymentMethod;
    
    private Integer customerId;

}
