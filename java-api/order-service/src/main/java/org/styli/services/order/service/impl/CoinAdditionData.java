package org.styli.services.order.service.impl;

import lombok.Data;

@Data
public class CoinAdditionData {
    private Integer customerId;
    private Integer storeId;
    private Integer coins;
    private Integer expiryInDays;
    private Boolean isReturn=false;
    private CoinAdditionDetailData detail;
}