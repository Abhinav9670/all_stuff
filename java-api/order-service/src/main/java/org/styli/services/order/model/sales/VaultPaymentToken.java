package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.sql.Timestamp;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

/**
 * Vault tokens of payment
 */
@Data
@Table(name = "vault_payment_token")
@Entity
public class VaultPaymentToken implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entity_id", insertable = false, nullable = false)
  private Integer entityId;

  @Column(name = "customer_id")
  private Integer customerId;

  @Column(name = "public_hash", nullable = false)
  private String publicHash;

  @Column(name = "payment_method_code", nullable = false)
  private String paymentMethodCode;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "created_at", nullable = false)
  private Timestamp createdAt;

  @Column(name = "expires_at")
  private Timestamp expiresAt;

  @Column(name = "gateway_token", nullable = false)
  private String gatewayToken;

  @Column(name = "details", columnDefinition = "TEXT")
  private String details;

  @Column(name = "is_active", nullable = false, columnDefinition = "BIT")
  private Integer active;

  @Column(name = "is_visible", nullable = false, columnDefinition = "BIT")
  private Integer visible;

}