package org.styli.services.order.pojo.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OTSOrderRequest {
    private String op;
    private Integer orderid;
    private Integer parentOrderId;
    private String incrementId;
    private String shipmentType;
    private Integer customerId;
    private String quoteId;
    private String customerEmail;
    private List<StatusMessage> statusMessage;
    private List<SkuItem> skus;
}

