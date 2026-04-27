package org.styli.services.order.model.Customer;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_address_entity_varchar")
@Getter
@Setter
public class CustomerAddressEntityVarchar implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "value_id")
	private Integer valueId;

	@Column(name = "attribute_id", columnDefinition = "SMALLINT")
	private Integer attributeId;

	@Column(name = "entity_id", columnDefinition = "SMALLINT")
	private Integer entityId;

	@Column(name = "value")
	private String value;

	@ManyToOne
	@JoinColumn(name = "entity_id", nullable = false, insertable = false, updatable = false)
	@JsonIgnore
	private CustomerAddressEntity customerAddressEntity;

}
