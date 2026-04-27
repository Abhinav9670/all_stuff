package org.styli.services.order.model.sales;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Sales Payment Transaction
 */
@Table(name = "sales_payment_transaction")
@Data
@Entity
public class SalesPaymentTransaction implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "transaction_id", insertable = false, nullable = false)
  private Integer transactionId;

  @Column(name = "parent_id")
  private Integer parentId;

  @Column(name = "order_id", nullable = false)
  private Integer orderId;

  @Column(name = "payment_id", nullable = false)
  private Integer paymentId;

  @Column(name = "txn_id")
  private String txnId;

  @Column(name = "parent_txn_id")
  private String parentTxnId;

  @Column(name = "txn_type")
  private String txnType;

  @Column(name = "is_closed", columnDefinition = "SMALLINT")
  private Integer isClosed;

  @Column(name = "additional_information", columnDefinition = "BLOB")
  private byte[] additionalInformation;

  @Column(name = "created_at")
  private Timestamp createdAt;

}