package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Sales Shipment Pack Details - stores pack box details for shipments
 */
@Table(name = "sales_shipment_pack_details")
@Setter
@Getter
@Entity
public class SalesShipmentPackDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id")
    private Integer entityId;

    @Column(name = "shipment_id", nullable = false)
    private Integer shipmentId;

    @Column(name = "length")
    private BigDecimal length;

    @Column(name = "breadth")
    private BigDecimal breadth;

    @Column(name = "height")
    private BigDecimal height;

    @Column(name = "weight")
    private BigDecimal weight;

    @Column(name = "box_id", nullable = false)
    private Long boxId;

    @Column(name = "box_code", nullable = false)
    private String boxCode;

    @Column(name = "vol_weight")
    private BigDecimal volWeight;

    // no longer used
    // @Column(name = "box_sku_id")
    // private String boxSkuId;

    @Column(name = "shipping_label", columnDefinition = "TEXT")
    private String shippingLabel;

    @Column(name = "way_bill")
    private String wayBill;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @Column(name = "gcs_object_path", length = 1024)
    private String gcsObjectPath;

    @Column(name = "gcs_signed_url_expiry")
    private Timestamp gcsSignedUrlExpiry;

    @Column(name = "carrier_backup_url", length = 1024)
    private String carrierBackupUrl;
}
