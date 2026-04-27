package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * Split Seller Shipment Item Entity
 */
@Entity
@Table(name = "split_seller_shipment_item")
@Data
public class SplitSellerShipmentItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer itemId;

    @Column(name = "row_total")
    private BigDecimal rowTotal = BigDecimal.ZERO;

    @Column(name = "price")
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "weight")
    private BigDecimal weight = BigDecimal.ZERO;

    @Column(name = "qty")
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "order_item_id")
    private Integer orderItemId;

    @Column(name = "additional_data", columnDefinition = "TEXT")
    private String additionalData;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sku")
    private String sku;

    @Column(name = "name")
    private String name;

    // Foreign Key Relationships

    @ManyToOne
    @JoinColumn(name = "parent_id", nullable = false, insertable = true, updatable = false)
    @JsonIgnore
    private SplitSellerShipment splitSellerShipment;
}
