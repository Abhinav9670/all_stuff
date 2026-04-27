package org.styli.services.order.model.sales;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.styli.services.order.model.Audit.AuditModel;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "proxy_order")
public class ProxyOrder extends AuditModel {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", insertable = false, nullable = false)
	private Long id;

	@Column(name = "quote_id", nullable = false)
	private String quoteId;

	@Column(name = "payment_id", nullable = false)
	private String paymentId;

	@Column(name = "quote", length = 16777215, columnDefinition = "longtext")
	private String quote;

	@Column(name = "sales_order", length = 16777215, columnDefinition = "longtext")
	private String salesOrder;
	
	@Column(name = "inventory_released")
	private boolean inventoryReleased;

	@Column(name = "payment_method")
	private String paymentMethod;
	
	@Column(name = "increment_id")
	private String incrementId;
	
	@Column(name = "status")
	private String status;
	
	@Column(name = "order_request", length = 16777215, columnDefinition = "longtext")
	private String orderRequest;
	
	@Column(name = "store_id")
	private Integer storeId;
	
	@Column(name = "customer_id")
	private Integer customerId;
	
	@Column(name = "email")
	private String email;
}
