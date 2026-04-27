package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Sales invoice item  
 */
@Getter
@Setter
@Entity
@Table(name = "sales_invoice_item")
public class SalesInvoiceItem implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entity_id", insertable = false, nullable = false)
  private Integer entityId;

  
  @Column(name = "base_row_total", nullable = false)
  private BigDecimal baseRowTotal = BigDecimal.ZERO;
  
  @Column(name = "base_price", nullable = false)
  private BigDecimal basePrice = BigDecimal.ZERO;
  
  @Column(name = "tax_amount")
  private BigDecimal taxAmount = BigDecimal.ZERO;
  
  @Column(name = "discount_amount")
  private BigDecimal discountAmount = BigDecimal.ZERO;
  
  @Column(name = "row_total", nullable = false)
  private BigDecimal rowTotal = BigDecimal.ZERO;
  
  @Column(name = "base_discount_amount")
  private BigDecimal baseDiscountAmount = BigDecimal.ZERO;

  @Column(name = "price_incl_tax")
  private BigDecimal priceInclTax;
  
  @Column(name = "base_tax_amount")
  private BigDecimal baseTaxAmount = BigDecimal.ZERO; 
  
  @Column(name = "qty")
  private BigDecimal quantity = BigDecimal.ZERO;
  
  @Column(name = "base_price_incl_tax")
  private BigDecimal basePriceInclTax;
  
  @Column(name = "base_cost")
  private BigDecimal baseCost = BigDecimal.ZERO;
  
  @Column(name = "price", nullable = false)
  private BigDecimal price = BigDecimal.ZERO;

  @Column(name = "discount_tax_compensation_amount")
  private BigDecimal discountTaxCompensationAmount;

  @Column(name = "base_discount_tax_compensation_amount")
  private BigDecimal baseDiscountTaxCompensationAmount;
  
  
  @Column(name = "tax_ratio", columnDefinition = "TEXT")
  private String taxRatio;
 

  @Column(name = "sku")
  private String sku;

  @Column(name = "name")
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;


  @Column(name = "additional_data", columnDefinition = "TEXT")
  private String additionalData;


  @Column(name = "base_row_total_incl_tax")
  private BigDecimal baseRowTotalInclTax;
  
  @Column(name = "row_total_incl_tax")
  private BigDecimal rowTotalInclTax;

  @Column(name = "order_item_id")
  private Integer orderItemId;

  @Column(name = "split_order_item_id")
  private Integer splitOrderItemId;
  
  @Column(name = "hsn_code", columnDefinition = "TEXT")
  private String hsnCode;

  @ManyToOne
  @JoinColumn(name = "parent_id", nullable = false, insertable = true, updatable = false)
  @JsonIgnore
  private SalesInvoice salesInvoice;


  @OneToMany(mappedBy = "salesInvoiceItem",  fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @OrderBy
  private Set<SalesInvoiceItemTax> salesInvoiceItemTax = new HashSet<>();
  
  public void addSalesInvoiceItemTax(SalesInvoiceItemTax salesInvoiceItemTaxVal) {
	    if (salesInvoiceItemTaxVal == null) {
	      return;
	    }
	    salesInvoiceItemTaxVal.setSalesInvoiceItem(this);
	    if (salesInvoiceItemTax == null) {
	    	salesInvoiceItemTax = new HashSet<>();
	    	salesInvoiceItemTax.add(salesInvoiceItemTaxVal);
	    } else if (!salesInvoiceItemTax.contains(salesInvoiceItemTaxVal)) {
	    	salesInvoiceItemTax.add(salesInvoiceItemTaxVal);
	    }
	  }

  

}