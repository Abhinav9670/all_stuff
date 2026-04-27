package org.styli.services.order.pojo.response.Order;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) 
public class OrderItem {

    private String parentOrderItemId;
    // private String orderItemId;

    private String parentProductId;
    // private String productId;

    public String name;
    public String sku;
    public String parentSku;
    public String price;
    public String originalPrice;
    public String discount;
    public String qty;
    public String qtyCanceled;
    public String qtyReturned;
    public String qtyReturnedInProcess;
    public String size;
    public String image;
    private String returnCategoryRestriction;
    private Boolean availableNow = false;
    public String amountRefund;
    public String refundInitiatedOn;
    
    private boolean isGiftProduct;

}
