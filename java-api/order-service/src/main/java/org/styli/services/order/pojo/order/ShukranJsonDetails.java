package org.styli.services.order.pojo.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ShukranJsonDetails {
    @JsonProperty("VirtualCardIdentifier")
    private String virtualCardIdentifier;
    @JsonProperty("VendorInvoiceNumber")
    private String vendorInvoiceNumber;
    @JsonProperty("InvoiceTotalQty")
    private Integer invoiceTotalQty=0;
    @JsonProperty("IsCoD")
    private String isCoD="N";
    @JsonProperty("IsOfflineTransaction")
    private String isOfflineTransaction= "N";
    @JsonProperty("IsRetroTransaction")
    private String isRetroTransaction= "N";
    @JsonProperty("CrossBorderFlag")
    private String crossBorderFlag= "N";
    @JsonProperty("LMSCartId")
    private String lMSCartId;
    @JsonProperty("PhoneNumber")
    private String phoneNumber;
    @JsonProperty("IsExternalPartnerTransaction")
    private String isExternalPartnerTransaction;
    @JsonProperty("ProcessReturnFlag")
    private String processReturnFlag;
    @JsonProperty("IsCancel")
    private String isCancel;
}
