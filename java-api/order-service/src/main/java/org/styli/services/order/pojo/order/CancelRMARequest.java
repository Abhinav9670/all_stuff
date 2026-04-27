package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author Umesh, 02/06/2020
 * @project product-service
 */

@Data
public class CancelRMARequest {

    @NotNull
    @Min(1)
    private Integer requestId;

    @NotNull
    @Min(1)
    private Integer customerId;

    @NotNull
    @Min(1)
    private Integer storeId;

}
