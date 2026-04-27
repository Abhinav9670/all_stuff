package org.styli.services.order.model.sales;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

/**
 * Split Sales Flat Order
 */
@Entity
@Getter
@Setter
@Table(name = "split_sales_order")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitSalesOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", nullable = false, updatable = false, insertable = true)
    private SalesOrder salesOrder;

    @OneToOne(mappedBy = "splitSalesOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @OrderBy
    private SplitSubSalesOrder splitSubSalesOrder;

    @OneToMany(mappedBy = "splitOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy
    private Set<SplitSellerOrder> splitSellerOrders = new HashSet<>();
  
    public void addSplitSellerOrder(SplitSellerOrder splitSellerOrderVal) {
  
      if (splitSellerOrderVal == null) {
        return;
      }
      splitSellerOrderVal.setSplitOrder(this);
      if (splitSellerOrders == null) {
        splitSellerOrders = new HashSet<>();
        splitSellerOrders.add(splitSellerOrderVal);
      } else if (!splitSellerOrders.contains(splitSellerOrderVal)) {
        splitSellerOrders.add(splitSellerOrderVal);
      }
    }

    @Column(name = "shipment_mode")
    private String shipmentMode;

    @Column(name = "has_global_shipment")
    private Boolean hasGlobalShipment = false;

    @Column(name = "estimated_delivery")
    private Timestamp estimatedDelivery;

    @Column(name = "state")
    private String state;

    @Column(name = "status")
    private String status;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "protect_code")
    private String protectCode;

    @Column(name = "shipping_description")
    private String shippingDescription;

    @Column(name = "is_virtual", columnDefinition = "SMALLINT")
    private Integer virtual;

    @Column(name = "store_id", columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "base_discount_amount")
    private BigDecimal baseDiscountAmount;

    @Column(name = "base_discount_canceled")
    private BigDecimal baseDiscountCanceled;

    @Column(name = "base_discount_invoiced")
    private BigDecimal baseDiscountInvoiced;

    @Column(name = "base_discount_refunded")
    private BigDecimal baseDiscountRefunded;

    @Column(name = "base_grand_total")
    private BigDecimal baseGrandTotal;

    @Column(name = "base_shipping_amount")
    private BigDecimal baseShippingAmount;

    @Column(name = "base_shipping_canceled")
    private BigDecimal baseShippingCanceled;

    @Column(name = "base_shipping_invoiced")
    private BigDecimal baseShippingInvoiced;

    @Column(name = "base_shipping_refunded")
    private BigDecimal baseShippingRefunded;

    @Column(name = "base_shipping_tax_amount")
    private BigDecimal baseShippingTaxAmount;

    @Column(name = "base_shipping_tax_refunded")
    private BigDecimal baseShippingTaxRefunded;

    @Column(name = "base_subtotal")
    private BigDecimal baseSubtotal;

    @Column(name = "base_subtotal_canceled")
    private BigDecimal baseSubtotalCanceled;

    @Column(name = "base_subtotal_invoiced")
    private BigDecimal baseSubtotalInvoiced;

    @Column(name = "base_subtotal_refunded")
    private BigDecimal baseSubtotalRefunded;

    @Column(name = "base_tax_amount")
    private BigDecimal baseTaxAmount;

    @Column(name = "base_tax_canceled")
    private BigDecimal baseTaxCanceled;

    @Column(name = "base_tax_invoiced")
    private BigDecimal baseTaxInvoiced;

    @Column(name = "base_tax_refunded")
    private BigDecimal baseTaxRefunded;

    @Column(name = "base_to_global_rate")
    private BigDecimal baseToGlobalRate;

    @Column(name = "base_to_order_rate")
    private BigDecimal baseToOrderRate;

    @Column(name = "base_total_canceled")
    private BigDecimal baseTotalCanceled;

    @Column(name = "base_total_invoiced")
    private BigDecimal baseTotalInvoiced;

    @Column(name = "base_total_invoiced_cost")
    private BigDecimal baseTotalInvoicedCost;

    @Column(name = "base_total_offline_refunded")
    private BigDecimal baseTotalOfflineRefunded;

    @Column(name = "base_total_online_refunded")
    private BigDecimal baseTotalOnlineRefunded;

    @Column(name = "base_total_paid")
    private BigDecimal baseTotalPaid;

    @Column(name = "base_total_qty_ordered")
    private BigDecimal baseTotalQtyOrdered;

    @Column(name = "base_total_refunded")
    private BigDecimal baseTotalRefunded;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "discount_canceled")
    private BigDecimal discountCanceled;

    @Column(name = "discount_invoiced")
    private BigDecimal discountInvoiced;

    @Column(name = "discount_refunded")
    private BigDecimal discountRefunded;

    @Column(name = "grand_total")
    private BigDecimal grandTotal;

    @Column(name = "shipping_amount")
    private BigDecimal shippingAmount;

    @Column(name = "shipping_canceled")
    private BigDecimal shippingCanceled;

    @Column(name = "shipping_invoiced")
    private BigDecimal shippingInvoiced;

    @Column(name = "shipping_refunded")
    private BigDecimal shippingRefunded;

    @Column(name = "shipping_tax_amount")
    private BigDecimal shippingTaxAmount;

    @Column(name = "shipping_tax_refunded")
    private BigDecimal shippingTaxRefunded;

    @Column(name = "store_to_base_rate")
    private BigDecimal storeToBaseRate;

    @Column(name = "store_to_order_rate")
    private BigDecimal storeToOrderRate;

    @Column(name = "subtotal")
    private BigDecimal subtotal;

    @Column(name = "subtotal_canceled")
    private BigDecimal subtotalCanceled;

    @Column(name = "subtotal_invoiced")
    private BigDecimal subtotalInvoiced;

    @Column(name = "subtotal_refunded")
    private BigDecimal subtotalRefunded;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "tax_canceled")
    private BigDecimal taxCanceled;

    @Column(name = "tax_invoiced")
    private BigDecimal taxInvoiced;

    @Column(name = "tax_refunded")
    private BigDecimal taxRefunded;

    @Column(name = "total_canceled")
    private BigDecimal totalCanceled;

    @Column(name = "total_invoiced")
    private BigDecimal totalInvoiced;

    @Column(name = "total_offline_refunded")
    private BigDecimal totalOfflineRefunded;

    @Column(name = "total_online_refunded")
    private BigDecimal totalOnlineRefunded;

    @Column(name = "total_paid")
    private BigDecimal totalPaid;

    @Column(name = "total_qty_ordered")
    private BigDecimal totalQtyOrdered;

    @Column(name = "total_refunded")
    private BigDecimal totalRefunded;

    @Column(name = "can_ship_partially", columnDefinition = "SMALLINT")
    private Integer canShipPartially;

    @Column(name = "can_ship_partially_item", columnDefinition = "SMALLINT")
    private Integer canShipPartiallyItem;

    @Column(name = "customer_is_guest", columnDefinition = "SMALLINT")
    private Integer customerIsGuest;

    @Column(name = "customer_note_notify", columnDefinition = "SMALLINT")
    private Integer customerNoteNotify;

    @Column(name = "billing_address_id")
    private Integer billingAddressId;

    @Column(name = "customer_group_id")
    private Integer customerGroupId;

    @Column(name = "edit_increment")
    private String editIncrement;

    @Column(name = "email_sent", columnDefinition = "SMALLINT")
    private Integer emailSent;

    @Column(name = "send_email", columnDefinition = "SMALLINT")
    private Integer sendEmail;

    @Column(name = "forced_shipment_with_invoice", columnDefinition = "SMALLINT")
    private Integer forcedShipmentWithInvoice;

    @Column(name = "payment_auth_expiration")
    private Integer paymentAuthExpiration;

    @Column(name = "quote_address_id")
    private Integer quoteAddressId;

    @Column(name = "quote_id")
    private Integer quoteId;

    @Column(name = "shipping_address_id")
    private Integer shippingAddressId;

    @Column(name = "adjustment_negative")
    private BigDecimal adjustmentNegative;

    @Column(name = "adjustment_positive")
    private BigDecimal adjustmentPositive;

    @Column(name = "base_adjustment_negative")
    private BigDecimal baseAdjustmentNegative;

    @Column(name = "base_adjustment_positive")
    private BigDecimal baseAdjustmentPositive;

    @Column(name = "base_shipping_discount_amount")
    private BigDecimal baseShippingDiscountAmount;

    @Column(name = "base_subtotal_incl_tax")
    private BigDecimal baseSubtotalInclTax;

    @Column(name = "base_total_due")
    private BigDecimal baseTotalDue;

    @Column(name = "payment_authorization_amount")
    private BigDecimal paymentAuthorizationAmount;

    @Column(name = "shipping_discount_amount")
    private BigDecimal shippingDiscountAmount;

    @Column(name = "subtotal_incl_tax")
    private BigDecimal subtotalInclTax;

    @Column(name = "total_due")
    private BigDecimal totalDue;

    @Column(name = "weight")
    private BigDecimal weight;

    @Column(name = "customer_dob")
    private Timestamp customerDob;

    @Column(name = "increment_id")
    private String incrementId;

    @Column(name = "applied_rule_ids")
    private String appliedRuleIds;

    @Column(name = "base_currency_code")
    private String baseCurrencyCode;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_firstname")
    private String customerFirstname;

    @Column(name = "customer_lastname")
    private String customerLastname;

    @Column(name = "customer_middlename")
    private String customerMiddlename;

    @Column(name = "customer_prefix")
    private String customerPrefix;

    @Column(name = "customer_suffix")
    private String customerSuffix;

    @Column(name = "customer_taxvat")
    private String customerTaxvat;

    @Column(name = "discount_description")
    private String discountDescription;

    @Column(name = "ext_customer_id")
    private String extCustomerId;

    @Column(name = "ext_order_id")
    private String extOrderId;

    @Column(name = "global_currency_code")
    private String globalCurrencyCode;

    @Column(name = "hold_before_state")
    private String holdBeforeState;

    @Column(name = "hold_before_status")
    private String holdBeforeStatus;

    @Column(name = "order_currency_code")
    private String orderCurrencyCode;

    @Column(name = "original_increment_id")
    private String originalIncrementId;

    @Column(name = "relation_child_id")
    private String relationChildId;

    @Column(name = "relation_child_real_id")
    private String relationChildRealId;

    @Column(name = "relation_parent_id")
    private String relationParentId;

    @Column(name = "relation_parent_real_id")
    private String relationParentRealId;

    @Column(name = "remote_ip")
    private String remoteIp;

    @Column(name = "shipping_method")
    private String shippingMethod;

    @Column(name = "store_currency_code")
    private String storeCurrencyCode;

    @Column(name = "store_name")
    private String storeName;

    @Column(name = "x_forwarded_for")
    private String xForwardedFor;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "total_item_count", nullable = false, columnDefinition = "SMALLINT")
    private Integer totalItemCount = 0;

    @Column(name = "customer_gender")
    private Integer customerGender;

    @Column(name = "discount_tax_compensation_amount")
    private BigDecimal discountTaxCompensationAmount;

    @Column(name = "base_discount_tax_compensation_amount")
    private BigDecimal baseDiscountTaxCompensationAmount;

    @Column(name = "shipping_discount_tax_compensation_amount")
    private BigDecimal shippingDiscountTaxCompensationAmount;

    @Column(name = "base_shipping_discount_tax_compensation_amnt")
    private BigDecimal baseShippingDiscountTaxCompensationAmnt;

    @Column(name = "discount_tax_compensation_invoiced")
    private BigDecimal discountTaxCompensationInvoiced;

    @Column(name = "base_discount_tax_compensation_invoiced")
    private BigDecimal baseDiscountTaxCompensationInvoiced;

    @Column(name = "discount_tax_compensation_refunded")
    private BigDecimal discountTaxCompensationRefunded;

    @Column(name = "base_discount_tax_compensation_refunded")
    private BigDecimal baseDiscountTaxCompensationRefunded;

    @Column(name = "shipping_incl_tax")
    private BigDecimal shippingInclTax;

    @Column(name = "base_shipping_incl_tax")
    private BigDecimal baseShippingInclTax;

    @Column(name = "coupon_rule_name")
    private String couponRuleName;

    @Column(name = "paypal_ipn_customer_notified")
    private Integer paypalIpnCustomerNotified = 0;

    @Column(name = "gift_message_id")
    private Integer giftMessageId;

    @Column(name = "original_shipping_amount", nullable = false)
    private BigDecimal originalShippingAmount = BigDecimal.ZERO;

    @Column(name = "base_original_shipping_amount", nullable = false)
    private BigDecimal baseOriginalShippingAmount = BigDecimal.ZERO;

    @Column(name = "cash_on_delivery_fee", nullable = false)
    private BigDecimal cashOnDeliveryFee = BigDecimal.ZERO;

    @Column(name = "base_cash_on_delivery_fee", nullable = false)
    private BigDecimal baseCashOnDeliveryFee = BigDecimal.ZERO;

    @Column(name = "wms_status", columnDefinition = "SMALLINT")
    private Integer wmsStatus = 0;

    @Column(name = "wms_pull_status", columnDefinition = "SMALLINT")
    private Integer wmsPullStatus = 0;

    @Column(name = "amstorecredit_base_amount")
    private BigDecimal amstorecreditBaseAmount;

    @Column(name = "amstorecredit_amount")
    private BigDecimal amstorecreditAmount;

    @Column(name = "amstorecredit_invoiced_base_amount")
    private BigDecimal amstorecreditInvoicedBaseAmount;

    @Column(name = "amstorecredit_invoiced_amount")
    private BigDecimal amstorecreditInvoicedAmount;

    @Column(name = "amstorecredit_refunded_base_amount")
    private BigDecimal amstorecreditRefundedBaseAmount;

    @Column(name = "amstorecredit_refunded_amount")
    private BigDecimal amstorecreditRefundedAmount;

    @Column(name = "to_refund")
    private BigDecimal toRefund;

    @Column(name = "source", columnDefinition = "SMALLINT")
    private int source;

    @Column(name = "merchant_reference")
    private String merchantReferance;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "order_data_updated", columnDefinition = "SMALLINT")
    private Integer orderDataUpdated = 0;

    @Column(name = "coupon_source_external", nullable = false)
    private Integer couponSourceExternal = 0;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancellation_reason_id")
    private Integer cancellationReasonId;

    @Column(name = "delivered_at")
    private Timestamp deliveredAt;

    @Column(name = "estimated_delivery_time")
    private Timestamp estimatedDeliveryTime;

    @Column(name = "clickpost_message")
    private String clickpostMessage;

    @Column(name = "import_fee")
    private BigDecimal importFee;

    @Column(name = "base_import_fee")
    private BigDecimal baseImportFee;

    @Column(name = "retry_payment", columnDefinition = "SMALLINT")
    private Integer retryPayment = 0;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "gift_voucher_discount", nullable = false)
    private BigDecimal giftVoucherDiscount = BigDecimal.ZERO;

    @Column(name = "refunded_import_fee")
    private BigDecimal refundedImportFee = BigDecimal.ZERO;

    @Column(name = "payfort_authorized", columnDefinition = "SMALLINT")
    private Integer payfortAuthorized = 0;

    @Column(name = "authorization_capture", columnDefinition = "SMALLINT")
    private Integer authorizationCapture = 0;

    @Column(name = "is_cancel_allowed")
    private Boolean isCancelAllowed = true;

    @OneToMany(mappedBy = "splitSalesOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<SplitSalesOrderPayment> splitSalesOrderPayments = new HashSet<>();

    @OneToMany(mappedBy = "splitSalesOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy
    private Set<SplitSalesOrderItem> splitSalesOrderItems = new HashSet<>();

    @OneToMany(mappedBy = "splitSalesOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private Set<SalesInvoice> splitSalesInvoices = new HashSet<>();

    @OneToMany(mappedBy = "splitSalesOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private Set<SalesShipment> splitSalesShipments = new HashSet<>();

    @OneToMany(mappedBy = "splitSalesOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private Set<SalesShipmentTrack> salesShipmentTrack = new HashSet<>();

    @OneToMany(mappedBy = "splitSalesOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy
    @JsonIgnore
    private Set<RtoAutoRefund> rtoAutoRefund = new HashSet<>();

    @OneToMany(mappedBy = "splitSalesOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy
    private Set<SalesInvoice> salesInvoices = new HashSet<>();

    public void addSalesInvoice(SalesInvoice salesInvoiceVal) {
        if (salesInvoiceVal == null) {
            return;
        }
        salesInvoiceVal.setSplitSalesOrder(this);
        if (splitSalesInvoices == null) {
            splitSalesInvoices = new HashSet<>();
            splitSalesInvoices.add(salesInvoiceVal);
        } else if (!splitSalesInvoices.contains(salesInvoiceVal)) {
            splitSalesInvoices.add(salesInvoiceVal);
        }
    }
}
