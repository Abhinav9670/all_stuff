package org.styli.services.order.pojo.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class ShukranEarnItem {
    @JsonProperty("DollarValueGross")
    private Integer dollarValueGross=0;
    @JsonProperty("DollarValueNet")
    private BigDecimal dollarValueNet;
    @JsonProperty("TaxAmount")
    private BigDecimal taxAmount;
    @JsonProperty("DiscountAmount")
    private BigDecimal discountAmount;
    @JsonProperty("ShippingAndHandlingAmount")
    private Integer shippingAndHandlingAmount;
    @JsonProperty("ItemNumber")
    private String itemNumber;
    @JsonProperty("ItemNumberTypeCode")
    private String itemNumberTypeCode;
    @JsonProperty("ItemDescription")
    private String itemDescription;
    @JsonProperty("Quantity")
    private Integer quantity;
    @JsonProperty("LineNumber")
    private Integer lineNumber;
    @JsonProperty("FulfillStoreCode")
    private String fulfillStoreCode;
    @JsonProperty("TransactionDateTime")
    private String transactionDateTime;
    @JsonProperty("TransactionNumber")
    private String transactionNumber;
    @JsonProperty("OriginalStoreCode")
    private String originalStoreCode;
    @JsonProperty("OriginalTransactionNumber")
    private String originalTransactionNumber;
    @JsonProperty("OriginalTransactionDateTime")
    private String originalTransactionDateTime;
    @JsonProperty("Uom")
    private String uom;
    @JsonProperty("JsonExternalData")
    private ShukranEarnItemDetails jsonExternalData;
}
