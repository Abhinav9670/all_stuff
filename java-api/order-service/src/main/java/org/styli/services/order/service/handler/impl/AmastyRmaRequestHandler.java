package org.styli.services.order.service.handler.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.order.model.rma.AmastyRmaRequest;
import org.styli.services.order.repository.Rma.AmastyRmaRequestRepository;
import org.styli.services.order.service.handler.ShippingLabelEntityHandler;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Handler for AmastyRmaRequest entity
 * Used for return shipments and RMA orders
 */
@Component
public class AmastyRmaRequestHandler implements ShippingLabelEntityHandler<AmastyRmaRequest> {

    @Autowired
    private AmastyRmaRequestRepository repository;
    
    @Override
    public String getShippingLabelUrl(AmastyRmaRequest entity) {
        return entity.getShippingLabel();
    }
    
    @Override
    public void setShippingLabelUrl(AmastyRmaRequest entity, String url) {
        entity.setShippingLabel(url);
    }
    
    @Override
    public String getGcsObjectPath(AmastyRmaRequest entity) {
        return entity.getGcsObjectPath();
    }
    
    @Override
    public void setGcsObjectPath(AmastyRmaRequest entity, String gcsPath) {
        entity.setGcsObjectPath(gcsPath);
    }
    
    @Override
    public Timestamp getGcsSignedUrlExpiry(AmastyRmaRequest entity) {
        return entity.getGcsSignedUrlExpiry();
    }
    
    @Override
    public void setGcsSignedUrlExpiry(AmastyRmaRequest entity, Timestamp expiry) {
        entity.setGcsSignedUrlExpiry(expiry);
    }
    
    @Override
    public String getCarrierBackupUrl(AmastyRmaRequest entity) {
        return entity.getCarrierBackupUrl();
    }
    
    @Override
    public void setCarrierBackupUrl(AmastyRmaRequest entity, String backupUrl) {
        entity.setCarrierBackupUrl(backupUrl);
    }
    
    @Override
    public String generateGcsObjectPath(AmastyRmaRequest entity) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        String year = now.format(DateTimeFormatter.ofPattern("yyyy"));
        String month = now.format(DateTimeFormatter.ofPattern("MM"));
        String timestamp = String.valueOf(System.currentTimeMillis());
        
        Integer orderId = entity.getOrderId();
        String rmaIncId = entity.getRmaIncId();
        
        return String.format("return-shipments/%s/%s/rma-%s_order-%d_%s.pdf",
                           year, month, rmaIncId, orderId, timestamp);
    }

    @Override
    public void saveEntity(AmastyRmaRequest entity) {
        entity.setModifiedAt(Timestamp.from(Instant.now()));
        repository.save(entity);
    }


    @Override
    public String getEntityIdentifier(AmastyRmaRequest entity) {
        return "RMA:" + entity.getRmaIncId() + ",OrderID:" + entity.getOrderId();
    }
    
    @Override
    public String getEntityTypeName() {
        return "AmastyRmaRequest";
    }
}
