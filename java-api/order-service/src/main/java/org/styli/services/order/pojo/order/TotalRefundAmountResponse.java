package org.styli.services.order.pojo.order;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TotalRefundAmountResponse {
    private BigDecimal totalRefundAmount;
    private BigDecimal transactionNetTotal;
    private Integer totalQty=0;
    private BigDecimal returnableQuantity= BigDecimal.ZERO;
    private BigDecimal refundShukranTotalPoints= BigDecimal.ZERO;
    private List<ShukranEarnItem> transactionDetails;
}
