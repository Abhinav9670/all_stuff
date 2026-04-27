package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.sales.SellerConfig;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for SellerConfig entity
 * Provides data access operations for seller_config table
 */
@Repository
public interface SellerConfigRepository extends JpaRepository<SellerConfig, Integer> {

    /**
     * Find seller config by ID
     * @param id the seller config ID
     * @return the seller config entity
     */
    SellerConfig findById(int id);

    /**
     * Find all seller configurations by seller ID
     * @param sellerId the seller identifier
     * @return list of seller configurations
     */
    List<SellerConfig> findBySellerId(String sellerId);

    /**
     * Find seller config by styli warehouse ID and seller warehouse ID combination
     * This combination is unique as per the database constraint
     * @param styliWarehouseId the styli warehouse ID
     * @param sellerWarehouseId the seller warehouse ID
     * @return optional seller config
     */
    Optional<SellerConfig> findByStyliWarehouseIdAndSellerWarehouseId(
            String styliWarehouseId, 
            String sellerWarehouseId
    );

    /**
     * Find all seller configurations by seller type
     * @param sellerType the seller type (apparel, unicommerce, seller_central_luna)
     * @return list of seller configurations
     */
    List<SellerConfig> findBySellerType(String sellerType);

    /**
     * Find all seller configurations by styli warehouse ID
     * @param styliWarehouseId the styli warehouse ID
     * @return list of seller configurations
     */
    List<SellerConfig> findByStyliWarehouseId(String styliWarehouseId);

    /**
     * Find seller config by seller warehouse ID
     * @param sellerWarehouseId the seller warehouse ID
     * @return list of seller configurations
     */
    List<SellerConfig> findBySellerWarehouseId(String sellerWarehouseId);

    /**
     * Find all seller configurations by seller ID and seller type
     * @param sellerId the seller identifier
     * @param sellerType the seller type
     * @return list of seller configurations
     */
    List<SellerConfig> findBySellerIdAndSellerType(String sellerId, String sellerType);

    /**
     * Check if a warehouse combination exists
     * @param styliWarehouseId the styli warehouse ID
     * @param sellerWarehouseId the seller warehouse ID
     * @return true if exists, false otherwise
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM seller_config " +
            "WHERE styli_warehouse_id = :styliWarehouseId " +
            "AND seller_warehouse_id = :sellerWarehouseId", 
            nativeQuery = true)
    boolean existsByWarehouseCombination(
            @Param("styliWarehouseId") String styliWarehouseId,
            @Param("sellerWarehouseId") String sellerWarehouseId
    );

    /**
     * Find all active seller configurations for a given seller
     * @param sellerId the seller identifier
     * @return list of seller configurations
     */
    @Query(value = "SELECT * FROM seller_config " +
            "WHERE SELLER_ID = :sellerId " +
            "ORDER BY created_at DESC", 
            nativeQuery = true)
    List<SellerConfig> findAllBySellerIdOrderByCreatedAtDesc(@Param("sellerId") String sellerId);

    /**
     * Find seller configurations by multiple seller IDs
     * @param sellerIds list of seller identifiers
     * @return list of seller configurations
     */
    @Query(value = "SELECT * FROM seller_config " +
            "WHERE SELLER_ID IN (:sellerIds)", 
            nativeQuery = true)
    List<SellerConfig> findBySellerIdIn(@Param("sellerIds") List<String> sellerIds);

    /**
     * Find all seller configurations by seller type with pagination support
     * @param sellerTypes list of seller types
     * @return list of seller configurations
     */
    @Query(value = "SELECT * FROM seller_config " +
            "WHERE seller_type IN (:sellerTypes)", 
            nativeQuery = true)
    List<SellerConfig> findBySellerTypeIn(@Param("sellerTypes") List<String> sellerTypes);

    @Query(value = "SELECT * FROM seller_config " +
            "WHERE seller_id = :sellerId AND styli_warehouse_id = :styliWarehouseId", 
            nativeQuery = true)
    List<SellerConfig> findBySellerIdAndStyliWarehouseId(@Param("sellerId") String sellerId, @Param("styliWarehouseId") String styliWarehouseId);

    /**
     * Direct DB fetch by Styli warehouse id (e.g. back order WMS cancel). Uses lowest id when multiple rows exist.
     */
    @Query(value = "SELECT * FROM seller_config WHERE styli_warehouse_id = :warehouseId ORDER BY id ASC LIMIT 1",
            nativeQuery = true)
    SellerConfig findSellerConfigForBackOrderByStyliWarehouseId(@Param("warehouseId") String warehouseId);

}

