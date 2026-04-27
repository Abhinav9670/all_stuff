package org.styli.services.order.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.math.BigDecimal;

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
	
	@Column(name = "is_external", columnDefinition = "SMALLINT")
	private Integer isExternal;
	
	@Column(name = "warehouse_location_code")
	private String warehouseLocationCode;
	
	@Column(name = "warehouse_inventory_table")
	private String warehouseInventoryTable;
	
	@Column(name = "currency")
	private String currency;

	@Column(name = "currency_conversion_rate")
	private BigDecimal currencyConversionRate;
	
	

}
