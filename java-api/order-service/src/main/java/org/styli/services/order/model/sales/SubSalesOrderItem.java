package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Sales Flat sub sales Order Item
 */
@Getter
@Setter
@Entity
@Table(name = "sub_sales_order_item")
public class SubSalesOrderItem implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "sub_item_id", insertable = false, nullable = false)
  private Integer SubitemId;


  @Column(name = "coupon_name")
  private String couponName;
  
  @Column(name = "coupon_type")
  private String couponType;

  @Column(name = "discount")
  private BigDecimal discount;

  @Column(name = "is_gift_voucher")
  private boolean isGiftVoucher;

  @Column(name = "gift_voucher_refunded_amount")
  private BigDecimal giftVoucherRefundedAmount = BigDecimal.ZERO;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "parent_order_id", nullable = false, insertable = true, updatable = false)
  @JsonIgnore
  private SalesOrder salesOrder;
  
  @ManyToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "main_item_id", nullable = false, insertable = true, updatable = false)
  @JsonIgnore
  private SalesOrderItem salesOrderItem;



}