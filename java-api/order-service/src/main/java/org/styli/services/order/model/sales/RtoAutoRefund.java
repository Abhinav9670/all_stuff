package org.styli.services.order.model.sales;

import java.math.BigDecimal;
import java.sql.Timestamp;

import javax.persistence.*;

import org.styli.services.order.model.Audit.AuditModel;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "rto_auto_refund")
public class RtoAutoRefund extends AuditModel {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", insertable = false, nullable = false)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	@JsonIgnore
	private SalesOrder salesOrder;

	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "split_order_id", nullable = true)
	@JsonIgnore
	private SplitSalesOrder splitSalesOrder;

	@Column(name = "customer_email")
	private String customerEmail;

	@Column(name = "increment_id", nullable = false, unique = true)
	private String incrementId;

	@Column(name = "payment_id", nullable = false)
	private String paymentId;

	@Column(name = "payment_method")
	private String paymentMethod;

	@Column(name = "order_amount")
	private BigDecimal orderAmount;

	@Column(name = "refund_amount")
	private BigDecimal refundAmount;

	@Column(name = "status")
	private String status;

	@Column(name = "refund_at", nullable = false)
	private Timestamp refundAt;

}
