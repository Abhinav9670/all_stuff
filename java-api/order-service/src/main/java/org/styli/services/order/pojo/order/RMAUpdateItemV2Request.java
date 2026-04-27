package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author Umesh, 14/07/2021
 * @project product-service
 */

@Data
public class RMAUpdateItemV2Request {

    @NotNull @Min(1)
    private Integer requestItemId;

    @NotNull @Min(1)
    private Integer returnQuantity;

    private Integer reasonId;

}
