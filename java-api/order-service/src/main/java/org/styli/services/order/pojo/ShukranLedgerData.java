package org.styli.services.order.pojo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ShukranLedgerData {
    private Integer customerId;
    private List<Integer> storeId;
    private String shukranProfileId;
    private String shukranCardNumber;
    private String orderId;
    private String orderIncrementId;
    private String reason;
    private BigDecimal points;
    private BigDecimal cashValueInBaseCurrency;
    private BigDecimal cashValueInCurrency;
    private Integer type=1;
    private Integer typeDetail;
    private String status;
    private ShukranLedgerOtherData otherDetail;
}
