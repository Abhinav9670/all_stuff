package org.styli.services.order.pojo.response;

import java.io.Serializable;

import lombok.Data;

@Data
public class CustomCouponRedemptionV5Response implements Serializable {

	private static final long serialVersionUID = 7519523691763347416L;

    private Integer code;
    private String status;
    private String message;
    private String trackingId;

}