package org.styli.services.order.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * @author Umesh, 30/04/2020
 * @project product-service
 */

@Data
public class CustomCouponValidationV4Request {

    private CouponValidationRequestV4Customer customer;

    private CouponValidationRequestV4Order order;

    private CouponValidationRequestV4Metadata metadata;

    private String coupon;

    @JsonProperty("check_for_auto_apply")
    private boolean checkForAutoApply;

    private String env;
    private String customerId;
    private String customerEmail;
    private String storeId;

}
