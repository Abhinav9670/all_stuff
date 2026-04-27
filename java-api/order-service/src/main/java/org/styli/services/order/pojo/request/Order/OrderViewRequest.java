package org.styli.services.order.pojo.request.Order;

import java.math.BigDecimal;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import java.util.List;

/**
 * @author Umesh, 12/05/2020
 * @project product-service
 */

@Data
public class OrderViewRequest {

    public Integer customerId;

    public Integer storeId;

    private Integer orderId;
    
    @JsonSetter("orderId")
    public void setOrderId(Object value) {
        if (value == null || "undefined".equals(String.valueOf(value))) {
            this.orderId = null;
            return;
        }
        if (value instanceof Number number) {
            this.orderId = number.intValue();
            return;
        }
        try {
            this.orderId = new java.math.BigDecimal(String.valueOf(value)).intValue();
        } catch (NumberFormatException e) {
            this.orderId = null;
        }
    }
    
    public Boolean emailSend;
    
    private String orderCode;

    private Boolean fetchStoreCreditBalance = false;

    private Boolean rmaItemQtyProcessed = true;
    
    private boolean useArchive;
    
    private String customerEmail;
    
    private String code;
    
    private Boolean showSellerCancelled;
    
    private List<PackboxDetails> packboxDetailsList;

    private BigDecimal packedQty;

    private List<ShipmentOrderV2> shipmentOrderList;
}
