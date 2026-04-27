package org.styli.services.order.model.sales;

import lombok.Getter;
import lombok.Setter;
import org.styli.services.order.converter.SellerAddressConverter;
import org.styli.services.order.converter.SellerBasicSettingsConverter;
import org.styli.services.order.converter.SellerConfigurationConverter;
import org.styli.services.order.pojo.SellerAddress;
import org.styli.services.order.pojo.SellerBasicSettings;
import org.styli.services.order.pojo.SellerConfiguration;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Seller Configuration Entity
 * Maps to seller_config table
 */
@Entity
@Table(name = "seller_config", 
    indexes = {
        @Index(name = "unique_warehouse_combination", 
               columnList = "styli_warehouse_id,seller_warehouse_id", 
               unique = true),
        @Index(name = "idx_seller_id", columnList = "SELLER_ID"),
        @Index(name = "idx_seller_type", columnList = "seller_type")
    }
)
@Getter
@Setter
public class SellerConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, nullable = false)
    private Integer id;

    @Column(name = "SELLER_ID", nullable = false, length = 255)
    private String sellerId;

    @Column(name = "styli_warehouse_id", nullable = false, length = 255)
    private String styliWarehouseId;

    @Column(name = "seller_warehouse_id", nullable = false, length = 255)
    private String sellerWarehouseId;

    @Column(name = "seller_type", nullable = false, length = 100)
    private String sellerType;

    @Column(name = "basic_settings", nullable = false, columnDefinition = "JSON")
    @Convert(converter = SellerBasicSettingsConverter.class)
    private SellerBasicSettings basicSettings;

    @Column(name = "configuration", nullable = false, columnDefinition = "JSON")
    @Convert(converter = SellerConfigurationConverter.class)
    private SellerConfiguration configuration;

    @Column(name = "address", nullable = false, columnDefinition = "JSON")
    @Convert(converter = SellerAddressConverter.class)
    private SellerAddress address;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "is_B2B_seller", columnDefinition = "SMALLINT")
    private Boolean isB2BSeller;

    @PrePersist
    protected void onCreate() {
        createdAt = new Timestamp(System.currentTimeMillis());
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = new Timestamp(System.currentTimeMillis());
    }
}

