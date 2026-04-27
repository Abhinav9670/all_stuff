package org.styli.services.order.pojo.request.GetShipmentV3;

import lombok.Data;

import java.util.Date;

@Data
public class Additional {
    private String delivery_type;
    private Boolean async;
    private Boolean label;
    private String order_date;
    private String vendor_code;
    private String duty_fee_paid_by;
    private String rvp_reason;
}
