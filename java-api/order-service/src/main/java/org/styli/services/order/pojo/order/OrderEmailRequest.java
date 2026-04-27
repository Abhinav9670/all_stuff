package org.styli.services.order.pojo.order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class OrderEmailRequest {

    @NotNull
    @Min(1)
    public Integer orderId;

}
