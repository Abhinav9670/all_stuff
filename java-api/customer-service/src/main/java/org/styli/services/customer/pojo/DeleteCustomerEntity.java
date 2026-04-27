package org.styli.services.customer.pojo;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

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

	@Column(name = "email")
	private String customerEmail;

	@Column(name = "marked_for_delete")
	private Integer markedForDelete;

	@Column(name = "ttl")
	private Date ttlTime;

	@Column(name = "reason")
	private String reason;

	@Column(name = "cron_processed", columnDefinition = "SMALLINT")
	private Integer cronProcessed = 0;

	@Column(name = "requested_at")
	private Date requestedAt;

	@Column(name = "withdrawn_at")
	private Date withdrawnAt;

	@Column(name = "completed_at")
	private Date completedAt;

//	@OneToOne(fetch = FetchType.LAZY)
//	@JoinColumn(name = "customer_id", nullable = false, updatable = false, insertable = true)
//	@JsonIgnore
//	private CustomerEntity customerEntity;
}
