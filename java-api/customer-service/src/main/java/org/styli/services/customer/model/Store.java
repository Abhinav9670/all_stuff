package org.styli.services.customer.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;

@Entity
@Table(name = "store", uniqueConstraints = { @UniqueConstraint(columnNames = "code", name = "uniqueNameConstraint") })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter

public class Store {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "store_id", columnDefinition = "SMALLINT")
	private Integer storeId;

	@Column(name = "code")
	private String code;

	@Column(name = "website_id", columnDefinition = "SMALLINT")
	private Integer webSiteId; // mapped required

	@Column(name = "group_id", columnDefinition = "SMALLINT")
	private String groupId; // mapped required

	@Column(name = "name")
	private String name;

	@Column(name = "sort_order", columnDefinition = "SMALLINT")
	private int sortOrder;

	@Column(name = "is_active", columnDefinition = "SMALLINT")
	private int isActive;

}
