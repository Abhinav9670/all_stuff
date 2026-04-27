package org.styli.services.order.pojo.response.V3;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class InvoiceDetail {
    private String channelSkuCode;
    private String orderItemCode;
    private BigDecimal netTaxAmountPerUnit;
    private BigDecimal netTaxAmountTotal;
    private BigDecimal baseSellingPricePerUnit;
    private BigDecimal baseSellingPriceTotal;
    private BigDecimal actualSellingPricePerUnit;
    private BigDecimal actualSellingPriceTotal;
    private Integer quantity;
    private List<TaxItem> taxItems;
}
