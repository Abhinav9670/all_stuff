package org.styli.services.order.model.Customer;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "delete_customers")
@Getter
@Setter
public class DeleteCustomerEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "entity_id")
	private Integer entityId;

	@Column(name = "customer_id")
	private Integer customerId;

	@Column(name = "marked_for_delete")
	private Integer markedForDelete;
	
	@Column(name = "ttl")
	private Date ttlTime;
	
	@Column(name = "reason")
	private String reason;

	@Column(name = "requested_at")
	private Date requestedAt;

	@Column(name = "withdrawn_at")
	private Date withdrawnAt;

	@Column(name = "completed_at")
	private Date completedAt;
}
