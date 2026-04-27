package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Split Seller Shipment Entity
 */
@Entity
@Table(name = "split_seller_shipment")
@Getter
@Setter
public class SplitSellerShipment implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @Column(name = "store_id", columnDefinition = "SMALLINT")
    private Integer storeId;

    @Column(name = "total_weight")
    private BigDecimal totalWeight;

    @Column(name = "total_qty")
    private BigDecimal totalQty;

    @Column(name = "email_sent", columnDefinition = "SMALLINT")
    private Integer emailSent;

    @Column(name = "send_email", columnDefinition = "SMALLINT")
    private Integer sendEmail;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "customer_id")
    private Integer customerId;

    @Column(name = "shipping_address_id")
    private Integer shippingAddressId;

    @Column(name = "billing_address_id")
    private Integer billingAddressId;

    @Column(name = "shipment_status")
    private Integer shipmentStatus;

    @Column(name = "increment_id")
    private String incrementId;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "packages", columnDefinition = "TEXT")
    private String packages;

    @Column(name = "shipping_label", columnDefinition = "MEDIUMBLOB")
    private String shippingLabel;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Column(name = "customer_note_notify", columnDefinition = "SMALLINT")
    private Integer customerNoteNotify;

    @Column(name = "cp_status", columnDefinition = "SMALLINT")
    private Integer cpStatus;

    @Column(name = "split_order_id")
    private Integer splitOrderId;

    @Column(name = "seller_order_id")
    private Integer sellerOrderId;

    // Foreign Key Relationships

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", nullable = true, insertable = false, updatable = false)
    @JsonIgnore
    private SalesOrder salesOrder;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "split_order_id", nullable = true, insertable = false, updatable = false)
    @JsonIgnore
    private SplitSalesOrder splitSalesOrder;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "seller_order_id", nullable = true, insertable = false, updatable = false)
    @JsonIgnore
    private SplitSellerOrder splitSellerOrder;

    // One-to-Many Relationship with Shipment Items

    @OneToMany(mappedBy = "splitSellerShipment", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy
    private Set<SplitSellerShipmentItem> splitSellerShipmentItems = new HashSet<>();

    // Helper Methods for managing relationships

    public void addSplitSellerShipmentItem(SplitSellerShipmentItem splitSellerShipmentItem) {
        if (splitSellerShipmentItem == null) {
            return;
        }
        splitSellerShipmentItem.setSplitSellerShipment(this);
        if (splitSellerShipmentItems == null) {
            splitSellerShipmentItems = new HashSet<>();
            splitSellerShipmentItems.add(splitSellerShipmentItem);
        } else if (!splitSellerShipmentItems.contains(splitSellerShipmentItem)) {
            splitSellerShipmentItems.add(splitSellerShipmentItem);
        }
    }
}
