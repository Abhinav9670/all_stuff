package org.styli.services.order.pojo.request;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @author Umesh, 18/03/2020
 * @project product-service
 */

@Data
public class ApplyCouponV4Request {

    @Min(1)
    public Integer customerId;

    @Min(1)
    public Integer quoteId;

    @NotNull
    @Min(1)
    public Integer storeId;

    @NotNull
    @NotBlank
    public String coupon;

    public Boolean couponSourceExternal = false;

}
