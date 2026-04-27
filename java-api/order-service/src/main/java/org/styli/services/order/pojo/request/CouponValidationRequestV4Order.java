package org.styli.services.order.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * @author Umesh, 30/04/2020
 * @project product-service
 */

@Data
public class CouponValidationRequestV4Order {

    private Integer amount;

    @JsonProperty("quote_id")
    private Integer quoteId;

    @JsonProperty("order_reference_id")
    private String orderReferenceId;

}
