package org.styli.services.order.model.sales;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.math.BigDecimal;

@Entity
@Table(name = "split_sub_seller_order")
@Getter
@Setter
public class SplitSubSellerOrder implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_order_id", nullable = false)
    private SplitSellerOrder splitSellerOrder;

    @Column(name = "shipment_mode")
    private String shipmentMode;

    @Column(name = "has_global_shipment")
    private Boolean hasGlobalShipment;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}


