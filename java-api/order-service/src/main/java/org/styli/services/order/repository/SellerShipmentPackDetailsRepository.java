package org.styli.services.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.sales.SellerShipmentPackDetails;

import java.util.List;

@Repository
public interface SellerShipmentPackDetailsRepository extends JpaRepository<SellerShipmentPackDetails, Integer> {
    
    List<SellerShipmentPackDetails> findByShipmentId(Integer shipmentId);
}
