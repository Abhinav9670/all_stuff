package org.styli.services.order.pojo.request.Order;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class ShipmentOrderV2 {
    private String orderCode;
    private BigDecimal packedQty;
    private List<PackboxDetails> packboxDetailsList;
}
