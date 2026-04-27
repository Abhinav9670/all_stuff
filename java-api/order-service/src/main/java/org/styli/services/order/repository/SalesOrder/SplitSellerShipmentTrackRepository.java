package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.styli.services.order.model.sales.SplitSellerShipmentTrack;

import java.util.List;

public interface SplitSellerShipmentTrackRepository
        extends JpaRepository<SplitSellerShipmentTrack, Integer>, JpaSpecificationExecutor<SplitSellerShipmentTrack> {
    
    List<SplitSellerShipmentTrack> findByOrderId(Integer orderId);
    
    List<SplitSellerShipmentTrack> findByParentId(Integer parentId);
    
    List<SplitSellerShipmentTrack> findBySellerId(String sellerId);
    
    List<SplitSellerShipmentTrack> findByTrackNumber(String trackNumber);
    
    List<SplitSellerShipmentTrack> findByAlphaAwb(String alphaAwb);
}
