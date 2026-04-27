package org.styli.services.order.service.handler.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.order.model.sales.SalesShipmentPackDetails;
import org.styli.services.order.repository.SalesShipmentPackDetailsRepository;
import org.styli.services.order.service.handler.ShippingLabelEntityHandler;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Handler for SalesShipmentPackDetails entity
 * Used for regular shipment pack boxes
 */
@Component
public class SalesShipmentPackDetailsHandler implements ShippingLabelEntityHandler<SalesShipmentPackDetails> {

    @Autowired
    private SalesShipmentPackDetailsRepository repository;

    @Override
    public String getShippingLabelUrl(SalesShipmentPackDetails entity) {
        return entity.getShippingLabel();
    }

    @Override
    public void setShippingLabelUrl(SalesShipmentPackDetails entity, String url) {
        entity.setShippingLabel(url);
    }

    @Override
    public String getGcsObjectPath(SalesShipmentPackDetails entity) {
        return entity.getGcsObjectPath();
    }

    @Override
    public void setGcsObjectPath(SalesShipmentPackDetails entity, String gcsPath) {
        entity.setGcsObjectPath(gcsPath);
    }

    @Override
    public Timestamp getGcsSignedUrlExpiry(SalesShipmentPackDetails entity) {
        return entity.getGcsSignedUrlExpiry();
    }

    @Override
    public void setGcsSignedUrlExpiry(SalesShipmentPackDetails entity, Timestamp expiry) {
        entity.setGcsSignedUrlExpiry(expiry);
    }
    
    @Override
    public String getCarrierBackupUrl(SalesShipmentPackDetails entity) {
        return entity.getCarrierBackupUrl();
    }
    
    @Override
    public void setCarrierBackupUrl(SalesShipmentPackDetails entity, String backupUrl) {
        entity.setCarrierBackupUrl(backupUrl);
    }
    
    @Override
    public String generateGcsObjectPath(SalesShipmentPackDetails entity) {
        // Format: forward-shipments/{year}/{month}/pack-{boxCode}_shipment-{shipmentId}_{timestamp}.pdf
        // Using UTC timezone for consistency across all handlers
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        String year = now.format(DateTimeFormatter.ofPattern("yyyy"));
        String month = now.format(DateTimeFormatter.ofPattern("MM"));
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        Integer shipmentId = entity.getShipmentId();
        String boxCode = entity.getBoxCode();
        
        return String.format("forward-shipments/%s/%s/pack-%s_shipment-%d_%s.pdf",
                           year, month, boxCode, shipmentId, timestamp);
    }

    @Override
    public void saveEntity(SalesShipmentPackDetails entity) {
        entity.setUpdatedAt(Timestamp.from(Instant.now()));
        repository.save(entity);
    }

    @Override
    public String getEntityIdentifier(SalesShipmentPackDetails entity) {
        return "PackDetails[id=" + entity.getEntityId() + ",shipmentId=" + entity.getShipmentId() + 
               ",boxCode=" + entity.getBoxCode() + "]";
    }

    @Override
    public String getEntityTypeName() {
        return "SalesShipmentPackDetails";
    }
}
