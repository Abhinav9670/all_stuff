package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * Sales Flat Shipment Track
 */
@Table(name = "sales_shipment_track")
@Data
@Entity
public class SalesShipmentTrack implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entity_id", insertable = false, nullable = false)
  private Integer entityId;

  @Column(name = "parent_id", nullable = false)
  private Integer parentId;

  @Column(name = "weight")
  private BigDecimal weight;

  @Column(name = "qty")
  private BigDecimal qty;

  @Column(name = "order_id", nullable = false)
  private Integer orderId;

  @Column(name = "split_sales_order_id")
  private Integer splitSalesOrderId;

  @Column(name = "track_number", columnDefinition = "TEXT")
  private String trackNumber;
  
  @Column(name = "alpha_awb", columnDefinition = "TEXT")
  private String alphaAwb;

  @Column(name = "shipping_label", columnDefinition = "TEXT")
  private String shippingLabel;

  @Column(name = "gcs_object_path", length = 1024)
  private String gcsObjectPath;

  @Column(name = "gcs_signed_url_expiry")
  private Timestamp gcsSignedUrlExpiry;

  @Column(name = "carrier_backup_url", length = 1024)
  private String carrierBackupUrl;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "title")
  private String title;

  @Column(name = "carrier_code")
  private String carrierCode;

  @Column(name = "created_at", nullable = false)
  private Timestamp createdAt;

  @Column(name = "updated_at", nullable = false)
  private Timestamp updatedAt;

  @Column(name = "invoice_upload_status", length = 20)
  private String invoiceUploadStatus;

  @Column(name = "invoice_upload_attempts", columnDefinition = "INT DEFAULT 0")
  private Integer invoiceUploadAttempts = 0;

  @Column(name = "invoice_upload_error", columnDefinition = "TEXT")
  private String invoiceUploadError;

  @Column(name = "invoice_uploaded_at")
  private Timestamp invoiceUploadedAt;

  @Column(name = "national_id_upload_status", length = 20)
  private String nationalIdUploadStatus;

  @Column(name = "national_id_upload_error", columnDefinition = "TEXT")
  private String nationalIdUploadError;

  @Column(name = "national_id_uploaded_at")
  private Timestamp nationalIdUploadedAt;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "order_id", nullable = false, insertable = false, updatable = false)
  @JsonIgnore
  private SalesOrder salesOrder;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "split_sales_order_id", nullable = true, insertable = false, updatable = false)
  @JsonIgnore
  private SplitSalesOrder splitSalesOrder;

}