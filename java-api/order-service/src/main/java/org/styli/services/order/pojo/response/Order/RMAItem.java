package org.styli.services.order.pojo.response.Order;

import lombok.Data;
import org.styli.services.order.pojo.order.BreakDownItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Umesh, 27/05/2020
 * @project product-service
 */

@Data
public class RMAItem {

    private String parentOrderItemId;

    private String requestId;
    private String rmaIncrementId;
    private String requestItemId;
    private String qty;

    private String parentProductId;
    private String name;
    private String sku;
    private String size;
    private String image;
    private String orderedQty;
//    Ordered qty minus the returned qty
    private String availableQty;
    private String returnedQty;
    private String returnCategoryRestriction;
    private boolean availableNow = false;

    private String price;
    private String originalPrice;
    private String discount;
    private String grandTotal;
    private String storeCreditRefundValue;
    private String returnCreatedAt;
    private String returnModifiedAt;
    private String status;
    private String statusLabel;
    private String description;
    private String statusColor;
    private String cancelCTA;

    private String reason;
    private String refundAt;
    
    private String awbNumber;
    private Boolean isReturnTypeDropOff=false;
    private String returnInvoiceLink;
    
    private String qcFailed;
    
    private String actualGrandTotal;
    
    private boolean partiallyRefundMessage;
    
    private boolean nonRefundMessage;
    
    private String rmaPaymentMethod;
    
    private String rmaPaymentLink;
    
    private String paymentExpireOn;
    
    private String refundTrasactionNumber;
    
    private String returnOutletType;

    private String coinValue;
    private BigDecimal shukranValue;
    private Integer shukranValueInCoins=0;
    private String subTotal;
    private String returnAmountFee;
    private String returnGrandTotal;
    private boolean returnChargeApplicable;
    private String returnAmountToBePay;
    private String returnFeePaymentMode;
    private String returnFeeMerchantReference;
    private List<BreakDownItem> returnBreakDown;
    private Boolean isShukranPaymentInOrder= false;
}
