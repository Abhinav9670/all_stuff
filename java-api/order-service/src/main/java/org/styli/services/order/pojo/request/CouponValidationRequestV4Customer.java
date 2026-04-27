package org.styli.services.order.pojo.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author Umesh, 30/04/2020
 * @project product-service
 */

@Data
public class CouponValidationRequestV4Customer {
    private String id;

    @JsonProperty("source_id")
    private String sourceId;

    private String name;

}
