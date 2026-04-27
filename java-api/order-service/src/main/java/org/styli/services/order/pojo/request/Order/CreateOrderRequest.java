package org.styli.services.order.pojo.request.Order;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class CreateOrderRequest {

    @NotNull
    @Min(1)
    public Integer quoteId;

    @NotNull
    @Min(1)
    public Integer storeId;

    public String ipAddress;

    public Integer source;

    public String merchantReference;

    public String appVersion;

}
