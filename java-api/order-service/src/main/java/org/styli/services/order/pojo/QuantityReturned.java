package org.styli.services.order.pojo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuantityReturned {
    private BigDecimal qtyReturned;
    private BigDecimal qtyReturnedInProcess;
    private BigDecimal qcFaildQty;
    private Integer qtyMissing;
}
