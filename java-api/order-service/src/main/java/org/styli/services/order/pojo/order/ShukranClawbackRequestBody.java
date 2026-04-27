package org.styli.services.order.pojo.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.styli.services.order.model.sales.ShukranTenders;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class ShukranClawbackRequestBody {
    @JsonProperty("ProfileId")
    private String profileId;
    @JsonProperty("TransactionTypeCode")
    private String transactionTypeCode="RT";
    @JsonProperty("GrossAmount")
    private String grossAmount;
    @JsonProperty("TransactionNetTotal")
    private BigDecimal transactionNetTotal;
    @JsonProperty("TransactionTotalTax")
    private String transactionTotalTax;
    @JsonProperty("DiscountAmount")
    private String discountAmount;
    @JsonProperty("CardNumber")
    private String cardNumber;
    @JsonProperty("CurrencyCode")
    private String currencyCode;
    @JsonProperty("TransactionDateTime")
    private String transactionDateTime;
    @JsonProperty("StoreCode")
    private String storeCode;
    @JsonProperty("TransactionNumber")
    private String transactionNumber;
    @JsonProperty("ProgramCode")
    private String programCode="SHUKRAN";
    @JsonProperty("Deviceid")
    private String deviceId;
    @JsonProperty("DeviceUserid")
    private String deviceUserid;
    @JsonProperty("OriginalStoreCode")
    private String originalStoreCode;
    @JsonProperty("OriginalTransactionNumber")
    private String originalTransactionNumber;
    @JsonProperty("OriginalTransactionDateTime")
    private String originalTransactionDateTime;
    @JsonProperty("ShippingAndHandling")
    private String shippingAndHandling;
    @JsonProperty("TransactionDetails")
    private List<ShukranEarnItem> transactionDetails;
    @JsonProperty("Tenders")
    private List<ShukranTenders> tenders;
    @JsonProperty("JsonExternalData")
    private ShukranJsonDetails jsonExternalData;
}

