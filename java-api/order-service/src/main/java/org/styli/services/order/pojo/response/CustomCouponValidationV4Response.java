package org.styli.services.order.pojo.response;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * @author Umesh, 30/04/2020
 * @project product-service
 */

@Data
public class CustomCouponValidationV4Response implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 7519523691763347416L;

    private Integer code;
    private String status;
    private String message;
    private CustomCouponValidationV4ResponseBody response;
    @JsonProperty("tracking_id")
    private String trackingId;

}
