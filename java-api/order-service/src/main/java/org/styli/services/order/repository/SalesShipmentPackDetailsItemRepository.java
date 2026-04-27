package org.styli.services.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.sales.SalesShipmentPackDetailsItem;

import java.util.List;

@Repository
public interface SalesShipmentPackDetailsItemRepository extends JpaRepository<SalesShipmentPackDetailsItem, Integer> {
    
    List<SalesShipmentPackDetailsItem> findByPackDetailsId(Integer packDetailsId);
    
    List<SalesShipmentPackDetailsItem> findByClientSkuIdIn(List<String> clientSkuIds);
    
    @Query("SELECT spi FROM SalesShipmentPackDetailsItem spi " +
           "JOIN spi.salesShipmentPackDetails spd " +
           "WHERE spi.clientSkuId IN :clientSkuIds AND spd.shipmentId = :shipmentId")
    List<SalesShipmentPackDetailsItem> findByClientSkuIdInAndShipmentId(@Param("clientSkuIds") List<String> clientSkuIds, 
                                                                        @Param("shipmentId") Integer shipmentId);
}
