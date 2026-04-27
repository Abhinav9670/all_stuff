package org.styli.services.order.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShippingAmountEntry {
    private String shipmentMode;
    private BigDecimal shipmentAmount;
    private BigDecimal shipmentThreshold;
    private BigDecimal remainThreshold;
    private BigDecimal remainshippingAmount;
    private String codCharges;
}
