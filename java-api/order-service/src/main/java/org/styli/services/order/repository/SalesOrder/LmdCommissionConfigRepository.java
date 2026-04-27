package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.LmdCommissionConfig;

import java.util.List;

public interface LmdCommissionConfigRepository extends JpaRepository<LmdCommissionConfig, Integer> {

        // soldBy matches concept_en OR concept_ar
        // short_description matches l4_category_en OR l4_category_ar
        @Query(value = """
        SELECT *
        FROM lmd_commission_config c
        WHERE (
            LOWER(TRIM(c.concept_en)) = LOWER(TRIM(:soldBy))
            OR LOWER(TRIM(c.concept_ar)) = LOWER(TRIM(:soldBy))
        )
        AND (
            LOWER(TRIM(c.l4_category_en)) = LOWER(TRIM(:shortDesc))
            OR LOWER(TRIM(c.l4_category_ar)) = LOWER(TRIM(:shortDesc))
        )
        LIMIT 1
        """, nativeQuery = true)
        List<LmdCommissionConfig> findCategoryMatch(
                @Param("soldBy") String soldBy,
                @Param("shortDesc") String shortDesc
        );


        // get seller row (concept match) to use default_commission
        @Query(value = """
        SELECT *
        FROM lmd_commission_config c
        WHERE (
            LOWER(TRIM(c.concept_en)) = LOWER(TRIM(:soldBy))
            OR LOWER(TRIM(c.concept_ar)) = LOWER(TRIM(:soldBy))
        ) AND (
         (c.l4_category_en IS NULL OR TRIM(c.l4_category_en) = '')
           AND (c.l4_category_ar IS NULL OR TRIM(c.l4_category_ar) = '')
        )
        LIMIT 1
        """, nativeQuery = true)
        List<LmdCommissionConfig> findSellerDefault(@Param("soldBy") String soldBy);
}