package org.styli.services.order.model.sales;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

/**
 * Sales Flat Invoice
 */
@Data
@Entity
@Table(name = "sales_invoice")
public class SalesInvoice implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "entity_id", insertable = false, nullable = false)
	private Integer entityId;

	@Column(name = "store_id", columnDefinition = "SMALLINT")
	private Integer storeId;

	//shukran keys started
	@Column(name="shukran_burned_points")
	private BigDecimal shukranBurnedPoints;

	@Column(name="shukran_burned_value_in_currency")
	private BigDecimal shukranBurnedValueInCurrency;

	@Column(name="shukran_burned_value_in_base_currency")
	private BigDecimal shukranBurnedValueInBaseCurrency;
	//shukran keys ended

	@Column(name = "base_grand_total")
	private BigDecimal baseGrandTotal;

	@Column(name = "shipping_tax_amount")
	private BigDecimal shippingTaxAmount;

	@Column(name = "tax_amount")
	private BigDecimal taxAmount;

	@Column(name = "base_tax_amount")
	private BigDecimal baseTaxAmount;

	@Column(name = "store_to_order_rate")
	private BigDecimal storeToOrderRate;

	@Column(name = "base_shipping_tax_amount")
	private BigDecimal baseShippingTaxAmount;

	@Column(name = "base_discount_amount")
	private BigDecimal baseDiscountAmount;

	@Column(name = "base_to_order_rate")
	private BigDecimal baseToOrderRate;

	@Column(name = "grand_total")
	private BigDecimal grandTotal;

	@Column(name = "shipping_amount")
	public BigDecimal shippingAmount;

	@Column(name = "subtotal_incl_tax")
	private BigDecimal subtotalInclTax;

	@Column(name = "base_subtotal_incl_tax")
	private BigDecimal baseSubtotalInclTax;

	@Column(name = "store_to_base_rate")
	private BigDecimal storeToBaseRate;

	@Column(name = "base_shipping_amount")
	private BigDecimal baseShippingAmount;

	@Column(name = "total_qty")
	private BigDecimal totalQty;

	@Column(name = "base_to_global_rate")
	private BigDecimal baseToGlobalRate;

	@Column(name = "subtotal")
	private BigDecimal subtotal;

	@Column(name = "base_subtotal")
	private BigDecimal baseSubtotal;

	@Column(name = "discount_amount")
	private BigDecimal discountAmount;

	@Column(name = "billing_address_id")
	private Integer billingAddressId;

	@Column(name = "is_used_for_refund", columnDefinition = "SMALLINT")
	private Integer isUsedForRefund;

	@Column(name = "email_sent", columnDefinition = "SMALLINT")
	private Integer emailSent;

	@Column(name = "send_email", columnDefinition = "SMALLINT")
	private Integer sendEmail;

	@Column(name = "can_void_flag", columnDefinition = "SMALLINT")
	private Integer canVoidFlag;

	@Column(name = "state")
	private Integer state;

	@Column(name = "shipping_address_id")
	private Integer shippingAddressId;

	@Column(name = "store_currency_code")
	private String storeCurrencyCode;

	@Column(name = "transaction_id")
	private String transactionId;

	@Column(name = "order_currency_code")
	private String orderCurrencyCode;

	@Column(name = "base_currency_code")
	private String baseCurrencyCode;

	@Column(name = "global_currency_code")
	private String globalCurrencyCode;

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

	@Column(name = "base_total_refunded")
	private BigDecimal baseTotalRefunded;

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

	// EAS Columns to coins start
	@Column(name = "eas_coins", columnDefinition = "MEDIUMINT")
	private Integer easCoins;

	@Column(name = "eas_value_in_currency")
	private BigDecimal easValueInCurrency;

	@Column(name = "eas_value_in_base_currency")
	private BigDecimal easValueInBaseCurrency;
	// EAS Columns to coins end

	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "order_id", nullable = false, insertable = true, updatable = false)
	@JsonIgnore
	private SalesOrder salesOrder;

	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "split_order_id", insertable = true, updatable = false)
	@JsonIgnore
	private SplitSalesOrder splitSalesOrder;

	@OneToMany(mappedBy = "salesInvoice", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OrderBy
	private Set<SalesInvoiceItem> salesInvoiceItem = new HashSet<>();

	public void addSalesInvoiceItem(SalesInvoiceItem salesInvoiceItemVal) {
		if (salesInvoiceItemVal == null) {
			return;
		}
		salesInvoiceItemVal.setSalesInvoice(this);
		if (salesInvoiceItem == null) {
			salesInvoiceItem = new HashSet<>();
			salesInvoiceItem.add(salesInvoiceItemVal);
		} else if (!salesInvoiceItem.contains(salesInvoiceItemVal)) {
			salesInvoiceItem.add(salesInvoiceItemVal);
		}
	}

	@Column(name = "cash_on_delivery_igst")
	private BigDecimal cashOnDeliveryIgst;

	@Column(name = "cash_on_delivery_sgst")
	private BigDecimal cashOnDeliverySgst;

	@Column(name = "cash_on_delivery_cgst")
	private BigDecimal cashOnDeliveryCgst;

	@Column(name = "shipping_igst")
	private BigDecimal shippingIgst;

	@Column(name = "shipping_sgst")
	private BigDecimal shippingSgst;

	@Column(name = "shipping_cgst")
	private BigDecimal shippingCgst;

	@Column(name = "max_igst_precent")
	private BigDecimal maxIgstPercent;

	@Column(name = "max_sgst_precent")
	private BigDecimal maxSgstPercent;

	@Column(name = "max_cgst_precent")
	private BigDecimal maxCgstPercent;

	@Column(name = "cash_on_delivery_tax_amount")
	private BigDecimal cashOnDeliveryTaxAmount;

	@Column(name = "zatca_status")
	private String zatcaStatus;

	@Column(name = "zatca_qr_code", columnDefinition = "TEXT")
	private String zatcaQRCode;
}