package org.styli.services.order.service.impl;

import lombok.Data;
import org.styli.services.order.pojo.order.ShukranEarnItem;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CalculateRefundAmountResponse {
    private String beforeCalculatedRefundAmount;
    private String AfterCalculatedRefundAmount;
    private String beforeReturnEasCoinValue;
    private String afterReturnEasCoinValue;
    private String beforeCreditAmount;
    private String afterCreditAmount;
    private String beforeAmastyCreditAmount;
    private String afterAmastyCreditAmount;
    private String beforeRefundOnlineAmount;
    private String afterRefundOnlineAmount;
    private Integer beforeCalculatedShukranPoints=0;
    private Integer afterCalculatedShukranPoints=0;
    private String beforeCalculatedShukranValue;
    private String afterCalculatedShukranValue;
    private BigDecimal orderNetPrice;
    private Integer totalQty=0;
    private List<ShukranEarnItem> TransactionDetails;
}
