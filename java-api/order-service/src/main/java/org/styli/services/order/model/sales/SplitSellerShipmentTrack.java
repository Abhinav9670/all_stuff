package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * Split Seller Shipment Track
 */
@Table(name = "split_seller_shipment_track")
@Data
@Entity
public class SplitSellerShipmentTrack implements Serializable {
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

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "title")
  private String title;

  @Column(name = "carrier_code")
  private String carrierCode;

  @Column(name = "seller_id")
  private String sellerId;

  @Column(name = "created_at", nullable = false)
  private Timestamp createdAt;

  @Column(name = "updated_at", nullable = false)
  private Timestamp updatedAt;

  @ManyToOne
  @JoinColumn(name = "order_id", nullable = false, insertable = false, updatable = false)
  @JsonIgnore
  private SalesOrder salesOrder;

  @ManyToOne
  @JoinColumn(name = "split_sales_order_id", nullable = false, insertable = false, updatable = false)
  @JsonIgnore
  private SplitSalesOrder splitSalesOrder;
}
