package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

/**
 * Sales Flat Order Grid
 */
@Data
@Table(name = "sales_order_grid")
@Entity
public class SalesOrderGrid implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "entity_id", insertable = false, nullable = false)
  private Integer entityId;

  @Column(name = "status")
  private String status;

  @Column(name = "store_id", columnDefinition = "SMALLINT")
  private Integer storeId;

  @Column(name = "store_name")
  private String storeName;

  @Column(name = "customer_id")
  private Integer customerId;

  @Column(name = "base_grand_total")
  private BigDecimal baseGrandTotal;

  @Column(name = "base_total_paid")
  private BigDecimal baseTotalPaid;

  @Column(name = "grand_total")
  private BigDecimal grandTotal;

  @Column(name = "total_paid")
  private BigDecimal totalPaid;

  @Column(name = "increment_id")
  private String incrementId;

  @Column(name = "base_currency_code")
  private String baseCurrencyCode;

  @Column(name = "order_currency_code")
  private String orderCurrencyCode;

  @Column(name = "shipping_name")
  private String shippingName;

  @Column(name = "billing_name")
  private String billingName;

  @Column(name = "created_at")
  private Timestamp createdAt;

  @Column(name = "updated_at")
  private Timestamp updatedAt;

  @Column(name = "billing_address")
  private String billingAddress;

  @Column(name = "shipping_address")
  private String shippingAddress;

  @Column(name = "shipping_information")
  private String shippingInformation;

  @Column(name = "customer_email")
  private String customerEmail;

  @Column(name = "customer_group")
  private String customerGroup;

  @Column(name = "subtotal")
  private BigDecimal subtotal;

  @Column(name = "shipping_and_handling")
  private BigDecimal shippingAndHandling;

  @Column(name = "customer_name")
  private String customerName;

  @Column(name = "payment_method")
  private String paymentMethod;

  @Column(name = "total_refunded")
  private BigDecimal totalRefunded;

  @Column(name = "signifyd_guarantee_status")
  private String signifydGuaranteeStatus;

  @Column(name = "to_refund")
  private BigDecimal toRefund;

  @Column(name = "source", columnDefinition = "SMALLINT")
  private Integer source;

  @Column(name = "app_version")
  private String appVersion;

  @Column(name = "is_split_order", columnDefinition = "SMALLINT")
  private Integer isSplitOrder = 0;

}