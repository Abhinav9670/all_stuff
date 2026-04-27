package org.styli.services.order.model.SalesOrder;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Sales Flat Creditmemo Grid
 */
@Table(name = "sales_creditmemo_grid")
@Entity
@Data
public class SalesCreditmemoGrid implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @Column(name = "increment_id")
    private String incrementId;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "order_increment_id")
    private String orderIncrementId;

    @Column(name = "order_created_at")
    private Timestamp orderCreatedAt;

    @Column(name = "billing_name")
    private String billingName;

    @Column(name = "state")
    private Integer state;

    @Column(name = "base_grand_total")
    private BigDecimal baseGrandTotal;

    @Column(name = "order_status")
    private String orderStatus;

    @Column(name = "store_id", columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "billing_address")
    private String billingAddress;

    @Column(name = "shipping_address")
    private String shippingAddress;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_group_id", columnDefinition = "SMALLINT")
    private Integer customerGroupId;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "shipping_information")
    private String shippingInformation;

    @Column(name = "subtotal")
    private BigDecimal subtotal;

    @Column(name = "shipping_and_handling")
    private BigDecimal shippingAndHandling;

    @Column(name = "adjustment_positive")
    private BigDecimal adjustmentPositive;

    @Column(name = "adjustment_negative")
    private BigDecimal adjustmentNegative;

    @Column(name = "order_base_grand_total")
    private BigDecimal orderBaseGrandTotal;

}