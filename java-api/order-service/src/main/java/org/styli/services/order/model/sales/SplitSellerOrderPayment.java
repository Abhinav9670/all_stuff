package org.styli.services.order.model.sales;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Split Seller Flat Order Payment
 */
@Data
@Entity
@Table(name = "split_seller_order_payment")
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitSellerOrderPayment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private SalesOrderPayment parentPayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_order_id")
    private SalesOrder mainOrder;

    @Column(name = "amount_paid")
    private Double amountPaid;

    @Column(name = "method")
    private String method;

}
