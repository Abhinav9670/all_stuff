package org.styli.services.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.sales.SellerShipmentPackDetailsItem;

import java.util.List;

@Repository
public interface SellerShipmentPackDetailsItemRepository extends JpaRepository<SellerShipmentPackDetailsItem, Integer> {
    
    List<SellerShipmentPackDetailsItem> findByPackDetailsId(Integer packDetailsId);
    
    List<SellerShipmentPackDetailsItem> findByClientSkuIdIn(List<String> clientSkuIds);
}
