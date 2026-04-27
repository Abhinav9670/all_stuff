package org.styli.services.order.model.sales;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import javax.persistence.*;

@Entity
@Getter
@Setter
@Table(
    name = "lmd_commission_config"
)
public class LmdCommissionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @Column(name = "concept_en")
    private String conceptEn;

    @Column(name = "concept_ar")
    private String conceptAr;

    @Column(name = "l4_category_en")
    private String l4CategoryEn;

    @Column(name = "l4_category_ar")
    private String l4CategoryAr;

    @Column(name = "category_commission")
    private BigDecimal categoryCommission;

    @Column(name = "default_commission")
    private BigDecimal defaultCommission;

    @Column(name = "ntd_discount")
    private BigDecimal ntdDiscount;

    @Column(name = "default_tax_percentage")
    private BigDecimal defaultTaxPercentage;

    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;
}
