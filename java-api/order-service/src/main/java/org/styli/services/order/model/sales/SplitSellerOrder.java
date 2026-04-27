package org.styli.services.order.model.sales;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "split_seller_order")
@Getter
@Setter
public class SplitSellerOrder implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @Column(name = "status")
    private String status;

    @Column(name = "wms_status", columnDefinition = "SMALLINT")
    private Integer wmsStatus = 0;

    @Column(name = "wms_pull_status", columnDefinition = "SMALLINT")
    private Integer wmsPullStatus = 0;

    @Column(name = "ext_order_id")
    private String extOrderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "split_order_id")
    private SplitSalesOrder splitOrder;

    @Column(name = "seller_id")
    private String sellerId;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @Column(name = "shipment_mode")
    private String shipmentMode;

    @Column(name = "increment_id")
    private String incrementId;

    @Column(name = "has_global_shipment")
    private Boolean hasGlobalShipment;

    @Column(name = "estimate_delivery")
    private Timestamp estimateDelivery;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "first_mile_warehouse_name")
    private String firstMileWarehouseName;

    @Column(name = "midmile_warehouse_id")
    private String midmileWarehouseId;

    @Column(name = "midmile_warehouse_name")
    private String midmileWarehouseName;

    @Column(name = "lastmile_warehouse_id")
    private String lastmileWarehouseId;

    @Column(name = "lastmile_warehouse_name")
    private String lastmileWarehouseName;

    @Column(name = "seller_central_acknowledgement", columnDefinition = "SMALLINT")
    private Integer sellerCentralAcknowledgement = 0;

    @Column(name = "timelines", columnDefinition = "TEXT")
    private String timeLines;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "awb_failed", columnDefinition = "SMALLINT")
    private Integer awbFailed = 0;

    @Column(name = "owner_seller_id")
    private String ownerSellerId = null;

    @OneToMany(mappedBy = "splitSellerOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy
    private Set<SplitSellerOrderItem> splitSellerOrderItems = new HashSet<>();

    @OneToMany(mappedBy = "splitSellerOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy
    private Set<SplitSellerShipment> splitSellerShipments = new HashSet<>();

    @OneToMany(mappedBy = "splitSellerOrder", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<SplitSubSellerOrder> splitSubSellerOrders = new HashSet<>();

    public void addSplitSellerOrderItem(SplitSellerOrderItem splitSellerOrderItemVal) {
        if (splitSellerOrderItemVal == null) {
            return;
        }
        splitSellerOrderItemVal.setSplitSellerOrder(this);
        if (splitSellerOrderItems == null) {
            splitSellerOrderItems = new HashSet<>();
            splitSellerOrderItems.add(splitSellerOrderItemVal);
        } else if (!splitSellerOrderItems.contains(splitSellerOrderItemVal)) {
            splitSellerOrderItems.add(splitSellerOrderItemVal);
        }
    }

public void addSplitSellerShipment(SplitSellerShipment splitSellerShipmentVal) {
    if (splitSellerShipmentVal == null) {
        return;
    }
    splitSellerShipmentVal.setSplitSellerOrder(this);
    if (splitSellerShipments == null) {
        splitSellerShipments = new HashSet<>();
    }
    if (!splitSellerShipments.contains(splitSellerShipmentVal)) {
        splitSellerShipments.add(splitSellerShipmentVal);
    }
}
}