package org.styli.services.order.model.rma;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Amasty RMA Request Table
 */
@Entity
@Setter
@Getter
@Table(name = "amasty_rma_tracking")
public class AmastyRmaTracking implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(insertable = false, name = "tracking_id", nullable = false)
    private Integer trackingId;

    @Column(name = "request_id", nullable = false)
    private Integer requestId;

    @Column(name = "tracking_code", nullable = false, columnDefinition = "TEXT")
    private String trackingCode;
    
    @Column(name = "tracking_number", nullable = false, columnDefinition = "TEXT")
    private String trackingNumber;
 
}