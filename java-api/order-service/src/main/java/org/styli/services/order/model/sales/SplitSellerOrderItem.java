package org.styli.services.order.model.sales;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "split_seller_order_item")
@Getter
@Setter
public class SplitSellerOrderItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id", insertable = false, nullable = false)
    private Integer itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_item_id", nullable = false)
    @JsonIgnore
    private SalesOrderItem salesOrderItem;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "split_sales_order_item_id")
    private SplitSalesOrderItem splitSalesOrderItem;

    @Column(name = "product_type")
    private String productType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_order_id")
    @JsonIgnore
    private SalesOrder mainOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seller_order_id")
    @JsonIgnore
    private SplitSellerOrder splitSellerOrder;

    @Column(name = "store_id")
    private Integer storeId;

    @ManyToOne(fetch = FetchType.LAZY , cascade = CascadeType.ALL)
    @JoinColumn(name = "parent_item_id", nullable = true, updatable = false, insertable = true)
    @JsonIgnore
    private SplitSellerOrderItem splitSellerOrderItem;

    @Column(name = "sku")
    private String sku;

    @Column(name = "seller_id")
    private String sellerId;
    @Column(name = "seller_name")
    private String sellerName;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @Column(name = "shipment_type")
    private String shipmentType;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "estimated_delivery_date")
    private Timestamp estimatedDeliveryDate;

    @Column(name = "min_estimated_date")
    private Timestamp minEstimatedDate;

    @Column(name = "max_estimated_date")
    private Timestamp maxEstimatedDate;

    @Column(name = "qty_canceled")
    private BigDecimal qtyCanceled = BigDecimal.ZERO;

    @Column(name = "qty_shipped")
    private BigDecimal qtyShipped = BigDecimal.ZERO;

    @Column(name = "qty_ordered")
    private BigDecimal qtyOrdered = BigDecimal.ZERO;

    @Column(name = "qty_packed")
    private BigDecimal qtyPacked = BigDecimal.ZERO;

    @Column(name = "cancelled_by", columnDefinition = "SMALLINT")
    private Integer cancelledBy = 0; // 1: by WMS, 2: by Seller, 3: by Customer

    @Column(name = "asn_number")
    private String asnNumber;

    @Column(name = "awb_tracking_number")
    private String awbTrackingNumber;

    @Column(name = "box_number")
    private String boxNumber;

    @Column(name = "seller_central_acknowledgement", columnDefinition = "SMALLINT")
    private Integer sellerCentralAcknowledgement = 0;
}