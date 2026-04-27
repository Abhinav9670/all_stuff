package org.styli.services.order.pojo.response.Order;

import java.math.BigDecimal;
import java.util.List;

import org.styli.services.order.db.product.pojo.Stores;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.model.sales.SalesOrder;
import org.styli.services.order.pojo.DiscountData;
import org.styli.services.order.pojo.order.RMAOrderV2Request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

import javax.persistence.Column;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderResponse {

    // shukran keys started
    private Integer totalShukranBurnedPoints;
    private BigDecimal totalShukranBurnedValueInCurrency;
    private BigDecimal totalShukranBurnedValueInBaseCurrency;
    private Integer totalShukranEarnedPoints;
    private BigDecimal totalShukranEarnedValueInCurrency;
    private BigDecimal totalShukranEarnedValueInBaseCurrency;
    private Integer shukranBasicEarnPoint;
    private Integer shukranBonusEarnPoint;
    private Boolean qualifiedPurchase=false;
    private String tierName;
    // shukran keys ended

    private String tabbyPaymentId;
    private Integer orderId;
    private String email;
    private String status;
    private String statusLabel;
    private String couponCode;
    private String shippingDescription;
    private String storeId;
    private String customerId;
    private String discountAmount;
    private String couponDiscountAmount;
    private String grandTotal;
    private String baseGrandTotal;
    private String shippingAmount;
    private String codCharges;
    private String subtotal;
    private String taxAmount;
    private String totalPaid;
    private String currency;

    private String cardNumber;
    private String paymentMethod;
    private String paymentOption;
    private String paymentResponseCode;
    private String paymentResponseMessage;
    private String storeCreditApplied;

    private String shortInfo;

    private String itemCount;
    private String billingAddressId;
    private String quoteId;
    private String shippingAddressId;
    private String incrementId;
    private String shippingMethod;

    private String createdAt;
    private String updatedAt;
    private String canceledAt;

    private String orderCreatedAt;
    private String orderUpdatedAt;

    private String deliveredAt;
    private String estimatedDeliveryTime;
    private String clickpostMessage;

    private OrderAddress shippingAddress;
    private List<OrderTracking> trackings;
    private List<OrderItem> products;
    private List<OrderItem> cancelProducts;
    private List<String> invoices;

    private Integer callToActionFlag;
    private Integer statusStepValue;
    private Integer statusColorStepValue;

    private String autoCouponApplied;
    private String autoCouponDiscount;

    private String importFeesAmount;
    //SFP-5 Enable Item Level Seller Cancellation for non KSA Countries
    private String refundedImportFeesAmount;

    private List<DiscountData> discountData;

    private String awbNumber;

    private String returnInvoiceLink;

    private Integer returnId;

    private String donationAmount;

    private String shippingUrl;

    private Integer rmaCount;
    private String returnFee;
    private Boolean isSecondRefundTagOn = false;

    //for drop off use
    @JsonIgnore
    private BigDecimal returnAmount;
    @JsonIgnore
    private Stores store;
    @JsonIgnore
    private SalesOrder order;
    @JsonIgnore
    private AmastyRmaRequest amastyRequest;
    @JsonIgnore
    private RMAOrderV2Request rmaRequest;

    private String storeName;

    private String billToName;

    private String ShipToName;

    private Boolean fullyCancelled;

    private Boolean partialCancelled;

    private String totalAmountRefund;

    private String purchaseTotal;

    private String toRefund;

    private String source;

    private String appVersion;

    private String styliCreditRefund;

    private String rmaIncId;

    private Boolean isRated;

    private boolean isRto;

    private String rtoStatus;

    private String rtoRefundAt;

    private String rtoRefundAmount;

    private int archived = 0;

    private String taxPercent;

    //EAS response for append
    private int spendCoin = 0;

    private String coinToCurrency = "";

    private String coinToBaseCurrency = "";
    //EAS response for append

    private boolean orderAlreadyExists;

    private boolean canRetryPayment;

    private String paymentExpiresAt;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String cpId;

    private String cityName;

    private String dropOffDetails;

    private Boolean isSplitOrder;
}