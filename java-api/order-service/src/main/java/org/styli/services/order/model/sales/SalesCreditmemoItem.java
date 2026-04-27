package org.styli.services.order.model.sales;

import lombok.Data;

import javax.persistence.*;

import org.styli.services.order.model.SalesOrder.SalesCreditmemo;
import org.styli.services.order.model.SalesOrder.SalesCreditmemoItemTax;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Sales Flat Creditmemo Item
 */
@Entity
@Table(name = "sales_creditmemo_item")
@Data
public class SalesCreditmemoItem implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "entity_id", insertable = false, nullable = false)
	private Integer entityId;

	@Column(name = "parent_id", nullable = false)
	private Integer parentId;

	@Column(name = "base_price")
	private BigDecimal basePrice;

	@Column(name = "tax_amount")
	private BigDecimal taxAmount;

	@Column(name = "base_row_total")
	private BigDecimal baseRowTotal;

	@Column(name = "discount_amount")
	private BigDecimal discountAmount;

	@Column(name = "row_total")
	private BigDecimal rowTotal;

	@Column(name = "base_discount_amount")
	private BigDecimal baseDiscountAmount;

	@Column(name = "price_incl_tax")
	private BigDecimal priceInclTax;

	@Column(name = "base_tax_amount")
	private BigDecimal baseTaxAmount;

	@Column(name = "base_price_incl_tax")
	private BigDecimal basePriceInclTax;

	@Column(name = "qty")
	private BigDecimal qty;

	@Column(name = "base_cost")
	private BigDecimal baseCost;

	@Column(name = "price")
	private BigDecimal price;

	@Column(name = "base_row_total_incl_tax")
	private BigDecimal baseRowTotalInclTax;

	@Column(name = "row_total_incl_tax")
	private BigDecimal rowTotalInclTax;

	@Column(name = "product_id")
	private String productId;

	@Column(name = "order_item_id")
	private Integer orderItemId;

	@Column(name = "additional_data", columnDefinition = "TEXT")
	private String additionalData;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Column(name = "sku")
	private String sku;

	@Column(name = "name")
	private String name;

	@Column(name = "discount_tax_compensation_amount")
	private BigDecimal discountTaxCompensationAmount;

	@Column(name = "base_discount_tax_compensation_amount")
	private BigDecimal baseDiscountTaxCompensationAmount;

	@Column(name = "tax_ratio", columnDefinition = "TEXT")
	private String taxRatio;

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
	
	@Column(name = "hsn_code", columnDefinition = "TEXT")
	private String hsnCode;
	
	@Column(name = "voucher_amount")
	private BigDecimal voucherAmount;
	
	@ManyToOne
	@JoinColumn(name = "parent_id", referencedColumnName = "entity_id", insertable = false, updatable = false)
	@JsonIgnore
	private SalesCreditmemo salesCreditMemo;

	@OneToMany(mappedBy = "salesCreditmemoItem", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@OrderBy
	private Set<SalesCreditmemoItemTax> salesCreditmemoItemTax = new HashSet<>();

	public void addSalesCreditmemoItemTax(SalesCreditmemoItemTax salesCreditmemoItemTaxVal) {
		if (salesCreditmemoItemTaxVal == null) {
			return;
		}
		salesCreditmemoItemTaxVal.setSalesCreditmemoItem(this);
		if (salesCreditmemoItemTax == null) {
			salesCreditmemoItemTax = new HashSet<>();
			salesCreditmemoItemTax.add(salesCreditmemoItemTaxVal);
		} else if (!salesCreditmemoItemTax.contains(salesCreditmemoItemTaxVal)) {
			salesCreditmemoItemTax.add(salesCreditmemoItemTaxVal);
		}
	}

}