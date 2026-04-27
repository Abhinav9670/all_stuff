package org.styli.services.order.model.SalesOrder;

import lombok.Data;

import javax.persistence.*;

import org.styli.services.order.model.sales.SalesCreditmemoItem;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

/**
 * Sales Flat Creditmemo
 */
@Data
@Entity
@Table(name = "sales_creditmemo")
public class SalesCreditmemo implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @Column(name = "store_id", columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "adjustment_positive")
    private BigDecimal adjustmentPositive;

    @Column(name = "memo_type")
    private String memoType;

    @Column(name = "base_shipping_tax_amount")
    private BigDecimal baseShippingTaxAmount;

    @Column(name = "store_to_order_rate")
    private BigDecimal storeToOrderRate;

    @Column(name = "base_discount_amount")
    private BigDecimal baseDiscountAmount;

    @Column(name = "base_to_order_rate")
    private BigDecimal baseToOrderRate;

    @Column(name = "grand_total")
    private BigDecimal grandTotal;

    @Column(name = "base_adjustment_negative")
    private BigDecimal baseAdjustmentNegative;

    @Column(name = "base_subtotal_incl_tax")
    private BigDecimal baseSubtotalInclTax;

    @Column(name = "shipping_amount")
    private BigDecimal shippingAmount;

    @Column(name = "subtotal_incl_tax")
    private BigDecimal subtotalInclTax;

    @Column(name = "adjustment_negative")
    private BigDecimal adjustmentNegative;

    @Column(name = "base_shipping_amount")
    private BigDecimal baseShippingAmount;

    @Column(name = "store_to_base_rate")
    private BigDecimal storeToBaseRate;

    @Column(name = "base_to_global_rate")
    private BigDecimal baseToGlobalRate;

    @Column(name = "base_adjustment")
    private BigDecimal baseAdjustment;

    @Column(name = "base_subtotal")
    private BigDecimal baseSubtotal;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "subtotal")
    private BigDecimal subtotal;

    @Column(name = "adjustment")
    private BigDecimal adjustment;

    @Column(name = "base_grand_total")
    private BigDecimal baseGrandTotal;

    @Column(name = "base_adjustment_positive")
    private BigDecimal baseAdjustmentPositive;

    @Column(name = "base_tax_amount")
    private BigDecimal baseTaxAmount;

    @Column(name = "shipping_tax_amount")
    private BigDecimal shippingTaxAmount;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "split_order_id")
    private Integer splitOrderId;

    @Column(name = "email_sent", columnDefinition = "SMALLINT")
    private Integer emailSent;

    @Column(name = "send_email", columnDefinition = "SMALLINT")
    private Integer sendEmail;

    @Column(name = "creditmemo_status")
    private Integer creditmemoStatus;

    @Column(name = "state")
    private Integer state;

    @Column(name = "shipping_address_id")
    private Integer shippingAddressId;

    @Column(name = "billing_address_id")
    private Integer billingAddressId;

    @Column(name = "invoice_id")
    private Integer invoiceId;

    @Column(name = "store_currency_code")
    private String storeCurrencyCode;

    @Column(name = "order_currency_code")
    private String orderCurrencyCode;

    @Column(name = "base_currency_code")
    private String baseCurrencyCode;

    @Column(name = "global_currency_code")
    private String globalCurrencyCode;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "increment_id")
    private String incrementId;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "discount_tax_compensation_amount")
    private BigDecimal discountTaxCompensationAmount;

    @Column(name = "base_discount_tax_compensation_amount")
    private BigDecimal baseDiscountTaxCompensationAmount;

    @Column(name = "shipping_discount_tax_compensation_amount")
    private BigDecimal shippingDiscountTaxCompensationAmount;

    @Column(name = "base_shipping_discount_tax_compensation_amnt")
    private BigDecimal baseShippingDiscountTaxCompensationAmnt;

    @Column(name = "shipping_incl_tax")
    private BigDecimal shippingInclTax;

    @Column(name = "base_shipping_incl_tax")
    private BigDecimal baseShippingInclTax;

    @Column(name = "discount_description")
    private String discountDescription;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Column(name = "customer_note_notify", columnDefinition = "SMALLINT")
    private Integer customerNoteNotify;

    @Column(name = "cash_on_delivery_fee", nullable = false)
    private BigDecimal cashOnDeliveryFee = BigDecimal.ZERO;

    @Column(name = "base_cash_on_delivery_fee", nullable = false)
    private BigDecimal baseCashOnDeliveryFee = BigDecimal.ZERO;

    @Column(name = "amstorecredit_base_amount")
    private BigDecimal amstorecreditBaseAmount;

    @Column(name = "amstorecredit_amount")
    private BigDecimal amstorecreditAmount;

    @Column(name = "rma_number", nullable = false)
    private String rmaNumber;
    
	// EAS_CHANGES quote data for spend data 3 columns added 
	@Column(name = "eas_coins",columnDefinition = "MEDIUMINT")
	private Integer easCoins;
	
	@Column(name = "eas_value_in_currency")
	private BigDecimal easValueInCurrency;
	
	@Column(name = "eas_value_in_base_currency")
	private BigDecimal easValueInBaseCurrency;
	
	@Column(name = "voucher_amount")
	private BigDecimal voucherAmount;

	@Column(name = "zatca_status")
	private String zatcaStatus;

	@Column(name = "zatca_qr_code", columnDefinition = "TEXT")
	private String zatcaQRCode;
	
	@Column(name = "reconciliation_reference")
	private String reconciliationReference;

    @Column(name= "sms_money")
    private BigDecimal smsMoney;

    // shukran keys started

    @Column(name="shukran_points_refunded")
    private BigDecimal shukranPointsRefunded;

    @Column(name="shukran_points_refunded_value_in_currency")
    private BigDecimal shukranPointsRefundedValueInCurrency;

    @Column(name="shukran_points_refunded_value_in_base_currency")
    private BigDecimal shukranPointsRefundedValueInBaseCurrency;

    // Shukran keys ended
	
	@OneToMany(mappedBy = "salesCreditMemo", cascade = CascadeType.ALL)
	@OrderBy
	private Set<SalesCreditmemoItem> salesCreditmemoItem = new HashSet<>();
	
}