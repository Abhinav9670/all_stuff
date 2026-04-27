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
 * Sales Flat Shipment
 */
@Table(name = "sales_shipment")
@Setter
@Getter
@Entity
public class SalesShipment implements Serializable {
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

    @Column(name = "order_id", nullable = false)
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

    @Column(name = "is_mps", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isMps = false;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "order_id", nullable = false, insertable = false, updatable = false)
    @JsonIgnore
    private SalesOrder salesOrder;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "split_order_id", insertable = true, updatable = false)
    @JsonIgnore
    private SplitSalesOrder splitSalesOrder;
    

    @OneToMany(mappedBy = "salesShipment", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy
    private Set<SalesShipmentItem> salesShipmentItem = new HashSet<>();


    public void addSalesShipmentItem(SalesShipmentItem salesShipmentVal) {
        if (salesShipmentVal == null) {
          return;
        }
        salesShipmentVal.setSalesShipment(this);
        if (salesShipmentItem == null) {
        	salesShipmentItem = new HashSet<>();
        	salesShipmentItem.add(salesShipmentVal);
        } else if (!salesShipmentItem.contains(salesShipmentVal)) {
        	salesShipmentItem.add(salesShipmentVal);
        }
      }

}