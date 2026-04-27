package org.styli.services.order.service.handler.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.order.model.sales.SalesShipmentTrack;
import org.styli.services.order.repository.SalesOrder.ShipmentTrackerRepository;
import org.styli.services.order.service.handler.ShippingLabelEntityHandler;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Handler for SalesShipmentTrack entity
 * Used for regular orders and seller orders
 */
@Component
public class SalesShipmentTrackHandler implements ShippingLabelEntityHandler<SalesShipmentTrack> {
    
    @Autowired
    private ShipmentTrackerRepository repository;
    
    @Override
    public String getShippingLabelUrl(SalesShipmentTrack entity) {
        return entity.getShippingLabel();
    }
    
    @Override
    public void setShippingLabelUrl(SalesShipmentTrack entity, String url) {
        entity.setShippingLabel(url);
    }
    
    @Override
    public String getGcsObjectPath(SalesShipmentTrack entity) {
        return entity.getGcsObjectPath();
    }
    
    @Override
    public void setGcsObjectPath(SalesShipmentTrack entity, String gcsPath) {
        entity.setGcsObjectPath(gcsPath);
    }
    
    @Override
    public Timestamp getGcsSignedUrlExpiry(SalesShipmentTrack entity) {
        return entity.getGcsSignedUrlExpiry();
    }
    
    @Override
    public void setGcsSignedUrlExpiry(SalesShipmentTrack entity, Timestamp expiry) {
        entity.setGcsSignedUrlExpiry(expiry);
    }
    
    @Override
    public String getCarrierBackupUrl(SalesShipmentTrack entity) {
        return entity.getCarrierBackupUrl();
    }
    
    @Override
    public void setCarrierBackupUrl(SalesShipmentTrack entity, String backupUrl) {
        entity.setCarrierBackupUrl(backupUrl);
    }
    
    @Override
    public String generateGcsObjectPath(SalesShipmentTrack entity) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        String year = now.format(DateTimeFormatter.ofPattern("yyyy"));
        String month = now.format(DateTimeFormatter.ofPattern("MM"));
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        Integer orderId = entity.getOrderId();
        Integer trackId = entity.getEntityId();
        
        return String.format("forward-shipments/%s/%s/order-%d_track-%d_%s.pdf",
                           year, month, orderId, trackId, timestamp);
    }
    
    @Override
    public void saveEntity(SalesShipmentTrack entity) {
        entity.setUpdatedAt(Timestamp.from(Instant.now()));
        repository.save(entity);
    }
    
    @Override
    public String getEntityIdentifier(SalesShipmentTrack entity) {
        return "TrackID:" + entity.getEntityId() + ",OrderID:" + entity.getOrderId();
    }
    
    @Override
    public String getEntityTypeName() {
        return "SalesShipmentTrack";
    }
}
