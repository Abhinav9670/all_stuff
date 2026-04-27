package org.styli.services.order.pojo.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ShukranEarnItemDetails {
    @JsonProperty("Lmsmultiplier")
    private String lmsmultiplier;
    @JsonProperty("PromoCode")
    private String promoCode;
    @JsonProperty("SaleFlag")
    private String saleFlag;
    @JsonProperty("ConceptCode")
    private int conceptCode=54;
    @JsonProperty("DepartmentCode")
    private String departmentCode;
    @JsonProperty("ProductName")
    private String productName;
    @JsonProperty("InvoiceTerritory")
    private String invoiceTerritory;
    @JsonProperty("IsBeautyBay")
    private String isBeautyBay;
    @JsonProperty("ItemDescription_AR")
    private String itemDescription_AR;
    @JsonProperty("ItemDescription")
    private String itemDescription;
}
