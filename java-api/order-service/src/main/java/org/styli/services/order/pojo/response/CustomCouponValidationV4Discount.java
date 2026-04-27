package org.styli.services.order.pojo.response;

import java.io.Serializable;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author Umesh, 30/04/2020
 * @project product-service
 */

@Data
public class CustomCouponValidationV4Discount implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 551805148512368866L;

    @JsonProperty("coupon_code")
    private String couponCode;

    @JsonProperty("redeem_type")
    private String redeemType;

    @JsonProperty("discount_type")
    private String discountType;

    private BigDecimal value;

    @JsonProperty("amount_limit")
    private Integer amountLimit;

    @JsonProperty("error_code")
    private Integer errorCode;

    @JsonProperty("message")
    private String message;
}
