package org.styli.services.order.pojo.braze;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class BrazePendingPaymentPush {


    @JsonProperty("order_id")
    private Integer orderId;
    
    @JsonProperty("customer_id")
    private Integer customerId;
    
    @JsonProperty("whatsapp_template")
    private String whatsAppTemplate;
    
    @JsonProperty("tot_items")
    private Integer totalItems;
    
    @JsonProperty("saving_discount")
    private Integer savingDiscount;
    
    @JsonProperty("url")
    private String deepLink;
    
    @JsonProperty("store_id")
    private Integer storeId;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("phone_number")
    private String phoneNumber;
    
    @JsonProperty("hours_left")
    private String hoursLeft;

}
