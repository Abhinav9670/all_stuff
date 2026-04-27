package org.styli.services.order.model.SalesOrder;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "seller_commission_details")
@Getter
@Setter
public class SellerCommissionDetails implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, nullable = false)
    private Integer id;

    @Column(name = "seller_name")
    private String sellerName;

    @Column(name = "seller_id")
    private Integer sellerId;

    @Column(name = "l4_category")
    private String l4Category;

    @Column(name = "commission_value")
    private BigDecimal commissionValue;

    // JSON string with keys like marketing, logistics_percentage, logistics_value
    @Column(name = "commission_details", columnDefinition = "TEXT")
    private String commissionDetails;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;
}



