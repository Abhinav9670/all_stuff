package org.styli.services.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.styli.services.order.model.sales.SalesShipmentPackDetails;

import java.util.List;

@Repository
public interface SalesShipmentPackDetailsRepository extends JpaRepository<SalesShipmentPackDetails, Integer> {
    
    List<SalesShipmentPackDetails> findByShipmentId(Integer shipmentId);
}
