package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

/**
 * Sales invoice grid  
 */
@Data
@Entity
@Table(name = "sales_invoice_grid")
public class SalesInvoiceGrid implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entity_id", insertable = false, nullable = false)
  private Integer entityId;
  
  @Column(name = "order_id", nullable = false)
  private Integer orderId;

  @Column(name = "base_currency_code")
  private String baseCurrencyCode;
  
  @Column(name = "base_grand_total")
  private BigDecimal baseGrandTotal = BigDecimal.ZERO;
  
  @Column(name = "billing_address")
  private String billingAddress;
  
  @Column(name = "billing_name")
  private String billingName;
  
  @Column(name = "created_at")
  private Timestamp createdAt;
	
  @Column(name = "updated_at")
  private Timestamp updatedAt;
  
  @Column(name = "customer_email")
  private String customerEmail;
  
  @Column(name = "customer_group_id")
  private Integer customerGroupId;
  
  @Column(name = "customer_name")
  private String customerName;
  
  @Column(name = "global_currency_code")
  private String globalCurrencyCode;
  
  @Column(name = "grand_total")
  private BigDecimal grandTotal = BigDecimal.ZERO;
  
  @Column(name = "increment_id")
  private String incrementId;
  
  @Column(name = "order_created_at")
  private Timestamp orderCreatedAt;
  
  @Column(name = "order_currency_code")
  private String orderCurrencyCode;
  
  @Column(name = "order_increment_id")
  private String orderIncrementId;
  
  @Column(name = "payment_method")
  private String paymentMethod;
  
  @Column(name = "shipping_address")
  private String shippingAddress;
  
  @Column(name = "shipping_and_handling")
  private BigDecimal shippingAndHandling = BigDecimal.ZERO;
  
  @Column(name = "shipping_information")
  private String shippingInformation;
  
  @Column(name = "state")
  private Integer state;
  
  @Column(name = "store_currency_code")
  private String storeCurrencyCode;
  
  @Column(name = "store_id",  columnDefinition = "SMALLINT")
  private Integer storeId;
  
  @Column(name = "store_name")
  private String storeName;

  @Column(name = "subtotal")
  private BigDecimal subTotal;
  
}