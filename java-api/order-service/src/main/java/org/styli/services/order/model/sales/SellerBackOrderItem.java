package org.styli.services.order.model.sales;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "seller_back_order_item")
@Getter
@Setter
public class SellerBackOrderItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "back_order_id", nullable = false)
    private SellerBackOrder sellerBackOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_order_id")
    private SplitSellerOrder splitSellerOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_order_id")
    private SalesOrder mainOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "split_order_id")
    private SplitSalesOrder splitOrder;

    @Column(name = "sku", length = 64, nullable = false)
    private String sku;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "shipment_code", length = 64)
    private String shipmentCode;

    @Column(name = "asn_code", length = 64)
    private String asnCode;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;
}