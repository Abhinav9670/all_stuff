package org.styli.services.order.model.sales;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Split Sales Flat Sub Sales Order Item
 */
@Getter
@Setter
@Entity
@Table(name = "split_sub_sales_order_item")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitSubSalesOrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "split_sub_item_id", insertable = false, nullable = false)
    private Integer splitSubItemId;

    @ManyToOne(fetch = FetchType.LAZY , cascade = CascadeType.ALL)
    @JoinColumn(name = "main_item_id", nullable = false, insertable = true, updatable = false)
    @JsonIgnore
    private SplitSalesOrderItem splitSalesOrderItem;

    @ManyToOne(fetch = FetchType.LAZY , cascade = CascadeType.ALL)
    @JoinColumn(name = "sub_item_id", nullable = false, insertable = true, updatable = false)
    @JsonIgnore
    private SubSalesOrderItem subSalesOrderItem;

    @Column(name = "coupon_name")
    private String couponName;

    @Column(name = "coupon_type")
    private String couponType;

    @Column(name = "discount")
    private BigDecimal discount;

    @Column(name = "is_gift_voucher", nullable = false)
    private boolean isGiftVoucher;

    @Column(name = "gift_voucher_refunded_amount", nullable = false)
    private BigDecimal giftVoucherRefundedAmount = BigDecimal.ZERO;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "parent_order_id", nullable = false, insertable = true, updatable = false)
    @JsonIgnore
    private SplitSalesOrder splitSalesOrder;

}
