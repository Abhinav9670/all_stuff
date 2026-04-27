package org.styli.services.order.pojo.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RefundShukranBurnedBodyData {
    @JsonProperty("Territory")
    private String territory;
    @JsonProperty("Concept")
    private Integer concept;
    @JsonProperty("StoreCode")
    private String storeCode;
    @JsonProperty("OriginalPRTxnNumber")
    private String originalPRTxnNumber;
    @JsonProperty("UniqueReferenceNumber")
    private String uniqueReferenceNumber;
}
