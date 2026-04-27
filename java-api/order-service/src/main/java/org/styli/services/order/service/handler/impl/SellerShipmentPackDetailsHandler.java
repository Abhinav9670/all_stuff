package org.styli.services.order.service.handler.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.styli.services.order.model.sales.SellerShipmentPackDetails;
import org.styli.services.order.repository.SellerShipmentPackDetailsRepository;
import org.styli.services.order.service.handler.ShippingLabelEntityHandler;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class SellerShipmentPackDetailsHandler implements ShippingLabelEntityHandler<SellerShipmentPackDetails> {

    @Autowired
    private SellerShipmentPackDetailsRepository repository;

    @Override
    public String getShippingLabelUrl(SellerShipmentPackDetails entity) {
        return entity.getShippingLabel();
    }

    @Override
    public void setShippingLabelUrl(SellerShipmentPackDetails entity, String url) {
        entity.setShippingLabel(url);
    }

    @Override
    public String getGcsObjectPath(SellerShipmentPackDetails entity) {
        return entity.getGcsObjectPath();
    }

    @Override
    public void setGcsObjectPath(SellerShipmentPackDetails entity, String gcsPath) {
        entity.setGcsObjectPath(gcsPath);
    }

    @Override
    public Timestamp getGcsSignedUrlExpiry(SellerShipmentPackDetails entity) {
        return entity.getGcsSignedUrlExpiry();
    }

    @Override
    public void setGcsSignedUrlExpiry(SellerShipmentPackDetails entity, Timestamp expiry) {
        entity.setGcsSignedUrlExpiry(expiry);
    }
    
    @Override
    public String getCarrierBackupUrl(SellerShipmentPackDetails entity) {
        return entity.getCarrierBackupUrl();
    }
    
    @Override
    public void setCarrierBackupUrl(SellerShipmentPackDetails entity, String backupUrl) {
        entity.setCarrierBackupUrl(backupUrl);
    }
    
    @Override
    public String generateGcsObjectPath(SellerShipmentPackDetails entity) {
        // Format: seller-shipments/{year}/{month}/shipment-{shipmentId}_box-{boxCode}_{timestamp}.pdf
        // Using UTC timezone for consistency across all handlers
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(now);
        
        return String.format("seller-shipments/%s/%s/shipment-%d_box-%s_%s.pdf",
                year,
                month,
                entity.getShipmentId(),
                entity.getBoxCode(),
                timestamp);
    }

    @Override
    public void saveEntity(SellerShipmentPackDetails entity) {
        entity.setUpdatedAt(Timestamp.from(Instant.now()));
        repository.save(entity);
    }

    @Override
    public String getEntityIdentifier(SellerShipmentPackDetails entity) {
        return String.format("SellerShipmentPackDetails[id=%d, shipmentId=%d, boxCode=%s]",
                entity.getEntityId(),
                entity.getShipmentId(),
                entity.getBoxCode());
    }

    @Override
    public String getEntityTypeName() {
        return "SellerShipmentPackDetails";
    }
}
