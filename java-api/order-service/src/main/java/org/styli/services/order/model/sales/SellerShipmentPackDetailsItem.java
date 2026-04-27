package org.styli.services.order.model.sales;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Sales Shipment Pack Details Item - stores SKU quantity data for each pack box
 */
@Table(name = "seller_shipment_pack_details_item")
@Setter
@Getter
@Entity
public class SellerShipmentPackDetailsItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id")
    private Integer entityId;

    @Column(name = "global_sku_id", nullable = false)
    private Long globalSkuId;

    @Column(name = "client_sku_id", nullable = false)
    private String clientSkuId;

    @Column(name = "count", nullable = false)
    private Integer count;

    @Column(name = "pack_details_id", nullable = false)
    private Integer packDetailsId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_details_id", nullable = false, insertable = false, updatable = false)
    @JsonIgnore
    private SellerShipmentPackDetails sellerShipmentPackDetails;
}
