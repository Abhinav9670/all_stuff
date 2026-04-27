package org.styli.services.order.pojo.response.Order;

import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class SplitOrderDTO {
    private boolean qualifiedPurchase;
    private Integer splitOrderId;
    private Integer splitOrderCount;
    private String splitIncrementId;
    private String shipmentMode;
    private String email;
    private String status;
    private String statusLabel;
    private String shippingDescription;
    private String storeId;
    private String customerId;
    private String discountAmount;
    private String grandTotal;
    private String baseGrandTotal;
    private String shippingAmount;
    private String codCharges;
    private String subtotal;
    private String currency;
    private String itemCount;
    private String billingAddressId;
    private String quoteId;
    private String shippingAddressId;
    private String incrementId;
    private String shippingMethod;
    private String createdAt;
    private String updatedAt;
    private Timestamp estimatedDeliveryTime;
    private Timestamp minimumEstimatedDeliveryTime;
    private Timestamp maximumEstimatedDeliveryTime;
    private List<OrderTracking> trackings;
    private List<OrderItem> products;
    private List<OrderItem> cancelProducts;
    private List<String> invoices;
    private Integer callToActionFlag;
    private int statusStepValue;
    private int statusColorStepValue;
    private String importFeesAmount;
    private String donationAmount;
    private String shippingUrl;
    private int rmaCount;
    private String returnFee;
    private Boolean isSecondRefundTagOn = false;
    private String rtoStatus;
    private Boolean  archived;
    private String taxPercent;
    private int spendCoin;
    private String coinToCurrency;
    private String coinToBaseCurrency;
    private boolean orderAlreadyExists;
    private boolean canRetryPayment;
    private boolean rto;
    private String canceledAt;
    private String deliveredAt;
    private String rtoRefundAt;
    private String rtoRefundAmount;
    private Boolean isRated;
}
