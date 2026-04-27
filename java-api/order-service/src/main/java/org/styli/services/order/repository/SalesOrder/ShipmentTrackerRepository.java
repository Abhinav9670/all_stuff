package org.styli.services.order.repository.SalesOrder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.styli.services.order.model.sales.SalesShipmentTrack;

import java.sql.Timestamp;
import java.util.List;

public interface ShipmentTrackerRepository
        extends JpaRepository<SalesShipmentTrack, Integer>, JpaSpecificationExecutor<SalesShipmentTrack> {
    
    List<SalesShipmentTrack> findByOrderId(Integer orderId);
    
    List<SalesShipmentTrack> findByParentId(Integer parentId);
    
    /**
     * Find shipment tracks with GCS shipping labels that are expiring soon
     * Used by scheduled job to refresh signed URLs before they expire
     */
    @Query("SELECT s FROM SalesShipmentTrack s WHERE s.gcsObjectPath IS NOT NULL " +
           "AND s.gcsSignedUrlExpiry IS NOT NULL " +
           "AND s.gcsSignedUrlExpiry < :expiryThreshold")
    List<SalesShipmentTrack> findExpiringShippingLabels(@Param("expiryThreshold") Timestamp expiryThreshold);

    List<SalesShipmentTrack> findByInvoiceUploadStatusInAndInvoiceUploadAttemptsLessThan(List<String> statuses, Integer maxAttempts);
}