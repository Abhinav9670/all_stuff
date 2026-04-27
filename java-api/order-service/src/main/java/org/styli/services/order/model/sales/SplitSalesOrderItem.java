package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Split Sales Flat Order Item
 */
@Getter
@Setter
@Entity
@Table(name = "split_sales_order_item")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitSalesOrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", insertable = false, nullable = false)
    private Integer itemId;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", nullable = false, insertable = true, updatable = false)
    private SalesOrder salesOrder;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "split_order_id", nullable = false, insertable = true, updatable = false)
    private SplitSalesOrder splitSalesOrder;

    @Column(name = "seller_name")
    private String sellerName;

    @Column(name = "shipment_type")
    private String shipmentType;

    @Column(name = "seller_id")
    private String sellerId;

    @Column(name = "warehouse_location_id")
    private String warehouseLocationId;

    @Column(name = "estimated_delivery_date")
    private Timestamp estimatedDeliveryDate;

    @Column(name = "min_estimated_date")
    private Timestamp minEstimatedDate;

    @Column(name = "max_estimated_date")
    private Timestamp maxEstimatedDate;

    @OneToOne(fetch = FetchType.LAZY , cascade = CascadeType.ALL)
    @JoinColumn(name = "parent_item_id", nullable = true, updatable = false, insertable = true)
    private SplitSalesOrderItem splitSalesOrderItem;

    @OneToOne(fetch = FetchType.LAZY , cascade = CascadeType.ALL)
    @JoinColumn(name = "sales_order_item_id", nullable = false, updatable = false, insertable = true)
    private SalesOrderItem salesOrderItem;

    @Column(name = "quote_item_id")
    private Integer quoteItemId;

    @Column(name = "store_id", columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "product_options", columnDefinition = "TEXT")
    private String productOptions;

    @Column(name = "weight")
    private BigDecimal weight = BigDecimal.ZERO;

    @Column(name = "is_virtual", columnDefinition = "SMALLINT")
    private Integer virtual;

    @Column(name = "sku")
    private String sku;

    @Column(name = "name")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "applied_rule_ids", columnDefinition = "TEXT")
    private String appliedRuleIds;

    @Column(name = "additional_data", columnDefinition = "TEXT")
    private String additionalData;

    @Column(name = "is_qty_decimal", columnDefinition = "SMALLINT")
    private Integer qtyDecimal;

    @Column(name = "no_discount", nullable = false, columnDefinition = "SMALLINT")
    private Integer noDiscount = 0;

    @Column(name = "qty_backordered")
    private BigDecimal qtyBackordered = BigDecimal.ZERO;

    @Column(name = "qty_canceled")
    private BigDecimal qtyCanceled = BigDecimal.ZERO;

    @Column(name = "qty_invoiced")
    private BigDecimal qtyInvoiced = BigDecimal.ZERO;

    @Column(name = "qty_ordered")
    private BigDecimal qtyOrdered = BigDecimal.ZERO;

    @Column(name = "qty_refunded")
    private BigDecimal qtyRefunded = BigDecimal.ZERO;

    @Column(name = "qty_shipped")
    private BigDecimal qtyShipped = BigDecimal.ZERO;

    @Column(name = "base_cost")
    private BigDecimal baseCost = BigDecimal.ZERO;

    @Column(name = "price", nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Column(name = "original_price")
    private BigDecimal originalPrice;

    @Column(name = "base_original_price")
    private BigDecimal baseOriginalPrice;

    @Column(name = "tax_percent")
    private BigDecimal taxPercent = BigDecimal.ZERO;

    @Column(name = "tax_amount")
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "base_tax_amount")
    private BigDecimal baseTaxAmount = BigDecimal.ZERO;

    @Column(name = "tax_invoiced")
    private BigDecimal taxInvoiced = BigDecimal.ZERO;

    @Column(name = "base_tax_invoiced")
    private BigDecimal baseTaxInvoiced = BigDecimal.ZERO;

    @Column(name = "discount_percent")
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "base_discount_amount")
    private BigDecimal baseDiscountAmount = BigDecimal.ZERO;

    @Column(name = "discount_invoiced")
    private BigDecimal discountInvoiced = BigDecimal.ZERO;

    @Column(name = "base_discount_invoiced")
    private BigDecimal baseDiscountInvoiced = BigDecimal.ZERO;

    @Column(name = "amount_refunded")
    private BigDecimal amountRefunded = BigDecimal.ZERO;

    @Column(name = "base_amount_refunded")
    private BigDecimal baseAmountRefunded = BigDecimal.ZERO;

    @Column(name = "row_total", nullable = false)
    private BigDecimal rowTotal = BigDecimal.ZERO;

    @Column(name = "base_row_total", nullable = false)
    private BigDecimal baseRowTotal = BigDecimal.ZERO;

    @Column(name = "row_invoiced", nullable = false)
    private BigDecimal rowInvoiced = BigDecimal.ZERO;

    @Column(name = "base_row_invoiced", nullable = false)
    private BigDecimal baseRowInvoiced = BigDecimal.ZERO;

    @Column(name = "row_weight")
    private BigDecimal rowWeight = BigDecimal.ZERO;

    @Column(name = "base_tax_before_discount")
    private BigDecimal baseTaxBeforeDiscount;

    @Column(name = "tax_before_discount")
    private BigDecimal taxBeforeDiscount;

    @Column(name = "ext_order_item_id")
    private String extOrderItemId;

    @Column(name = "locked_do_invoice", columnDefinition = "SMALLINT")
    private Integer lockedDoInvoice;

    @Column(name = "locked_do_ship", columnDefinition = "SMALLINT")
    private Integer lockedDoShip;

    @Column(name = "price_incl_tax")
    private BigDecimal priceInclTax;

    @Column(name = "base_price_incl_tax")
    private BigDecimal basePriceInclTax;

    @Column(name = "row_total_incl_tax")
    private BigDecimal rowTotalInclTax;

    @Column(name = "base_row_total_incl_tax")
    private BigDecimal baseRowTotalInclTax;

    @Column(name = "discount_tax_compensation_amount")
    private BigDecimal discountTaxCompensationAmount;

    @Column(name = "base_discount_tax_compensation_amount")
    private BigDecimal baseDiscountTaxCompensationAmount;

    @Column(name = "discount_tax_compensation_invoiced")
    private BigDecimal discountTaxCompensationInvoiced;

    @Column(name = "base_discount_tax_compensation_invoiced")
    private BigDecimal baseDiscountTaxCompensationInvoiced;

    @Column(name = "discount_tax_compensation_refunded")
    private BigDecimal discountTaxCompensationRefunded;

    @Column(name = "base_discount_tax_compensation_refunded")
    private BigDecimal baseDiscountTaxCompensationRefunded;

    @Column(name = "tax_canceled")
    private BigDecimal taxCanceled;

    @Column(name = "discount_tax_compensation_canceled")
    private BigDecimal discountTaxCompensationCanceled;

    @Column(name = "tax_refunded")
    private BigDecimal taxRefunded;

    @Column(name = "base_tax_refunded")
    private BigDecimal baseTaxRefunded;

    @Column(name = "discount_refunded")
    private BigDecimal discountRefunded;

    @Column(name = "base_discount_refunded")
    private BigDecimal baseDiscountRefunded;

    @Column(name = "gift_message_id")
    private Integer giftMessageId;

    @Column(name = "gift_message_available")
    private Integer giftMessageAvailable;

    @Column(name = "free_shipping", nullable = false, columnDefinition = "SMALLINT")
    private Integer freeShipping = 0;

    @Column(name = "weee_tax_applied", columnDefinition = "TEXT")
    private String weeeTaxApplied;

    @Column(name = "weee_tax_applied_amount")
    private BigDecimal weeeTaxAppliedAmount;

    @Column(name = "weee_tax_applied_row_amount")
    private BigDecimal weeeTaxAppliedRowAmount;

    @Column(name = "weee_tax_disposition")
    private BigDecimal weeeTaxDisposition;

    @Column(name = "weee_tax_row_disposition")
    private BigDecimal weeeTaxRowDisposition;

    @Column(name = "base_weee_tax_applied_amount")
    private BigDecimal baseWeeeTaxAppliedAmount;

    @Column(name = "base_weee_tax_applied_row_amnt")
    private BigDecimal baseWeeeTaxAppliedRowAmnt;

    @Column(name = "base_weee_tax_disposition")
    private BigDecimal baseWeeeTaxDisposition;

    @Column(name = "base_weee_tax_row_disposition")
    private BigDecimal baseWeeeTaxRowDisposition;

    @Column(name = "original_base_price")
    private BigDecimal originalBasePrice;

    @Column(name = "parent_sku")
    private String parentSku;

    @Column(name = "seller_qty_cancelled")
    private BigDecimal sellerQtyCancelled;

    @Column(name = "returnable")
    private Integer returnable;

    @Column(name = "hsn_code")
    private String hsnCode;

    @Column(name = "item_img_url", columnDefinition = "TEXT")
    private String itemImgUrl;

    @Column(name = "item_brand_name")
    private String itemBrandName;

    @Column(name = "item_size")
    private String itemSize;

    @Column(name = "shukran_coins_earned")
    private BigDecimal shukranCoinsEarned;

    @Column(name = "shukran_coins_burned")
    private BigDecimal shukranCoinsBurned;

    @Column(name = "shukran_coins_burned_value_in_base_currency")
    private BigDecimal shukranCoinsBurnedValueInBaseCurrency;

    @Column(name = "shukran_coins_burned_value_in_currency")
    private BigDecimal shukranCoinsBurnedValueInCurrency;

    @Column(name = "shukran_coins_earned_value_in_currency")
    private BigDecimal shukranCoinsEarnedValueInCurrency;

    @Column(name = "shukran_coins_earned_value_in_base_currency")
    private BigDecimal shukranCoinsEarnedValueInBaseCurrency;

    @Column(name = "shukran_l4_category")
    private String shukranL4Category;

    @Column(name = "on_sale")
    private Boolean onSale;

    @Column(name = "store_credit_amount")
    private BigDecimal storeCreditAmount;

    @Column(name = "payfort_amount")
    private BigDecimal payfortAmount;

    @Column(name = "bnpl_amount")
    private BigDecimal bnplAmount;

    @Column(name = "vendor_sku")
    private String vendorSku;

    @OneToMany(mappedBy = "splitSalesOrderItem", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @OrderBy
    private Set<SplitSubSalesOrderItem> splitSubSalesOrderItem = new HashSet<>();

    public void addSplitSubSalesOrderItem(SplitSubSalesOrderItem splitSubSalesOrderItemVal) {
        if (splitSubSalesOrderItemVal == null) {
            return;
        }
        splitSubSalesOrderItemVal.setSplitSalesOrderItem(this);
        if (splitSubSalesOrderItem == null) {
            splitSubSalesOrderItem = new HashSet<>();
            splitSubSalesOrderItem.add(splitSubSalesOrderItemVal);
        } else if (!splitSubSalesOrderItem.contains(splitSubSalesOrderItemVal)) {
            splitSubSalesOrderItem.add(splitSubSalesOrderItemVal);
        }
    }

}
