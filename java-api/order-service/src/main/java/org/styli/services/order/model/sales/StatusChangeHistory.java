package org.styli.services.order.model.sales;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Salesrule Coupon
 */
@Data
@Entity
@Table(name = "status_change_history")
public class StatusChangeHistory implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, nullable = false)
    private Integer id;

    @Column(name = "order_id", nullable = false)
    private String orderId;
    @Column(name = "order_increment_id", nullable = false)
    private String orderIncrementId;
  
    @Column(name = "processing_date")
    private Timestamp processingDate;
    
    @Column(name = "pending_payment_date")
    private Timestamp pendingPaymentDate;
    
    @Column(name = "cancel_date")
    private Timestamp cancelDate;
   
    @Column(name = "packed_date")
    private Timestamp packedDate;
    
    @Column(name = "shipped_date")
    private Timestamp shippedDate;
       
    @Column(name = "rto_date")
    private Timestamp rtoDate;
    
    @Column(name = "delivered_date")
    private Timestamp deliveredDate;
    
    @Column(name = "rma_created_at")
    private Timestamp rmaCreatedDate;
      
    @Column(name = "picked_up_date")
    private Timestamp pickedUpDate;
    
    @Column(name = "refunded_date")
    private Timestamp refundedDate;
       
    @Column(name = "received_warehouse_date")
    private Timestamp receivedWarehouseDate;
    
    @Column(name = "rma_cancel_date")
    private Timestamp rmaCancelDate;
    
    @Column(name = "created_at")
    private Timestamp createAt;
    
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @Column(name = "split_order_id", nullable = true)
    private Integer splitOrderId;

    @Column(name = "split_order_increment_id", nullable = true)
    private String splitOrderIncrementId;
}