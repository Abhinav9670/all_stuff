package org.styli.services.order.model.sales;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

/**
 * Sales Flat Shipment Track
 */
@Table(name = "amasty_rma_tracking")
@Data
@Entity
public class ReturnShipmentTrack implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "tracking_id", insertable = false, nullable = false)
  private Integer trackingId;

  @Column(name = "request_id", nullable = false)
  private Integer requestId;

  @Column(name = "tracking_code", nullable = false)
  private String trackingCode;

  @Column(name = "tracking_number", columnDefinition = "TEXT")
  private String trackNumber;

  @Column(name = "alpha_awb", columnDefinition = "TEXT")
  private String alphaAwb;

  
  
 
}