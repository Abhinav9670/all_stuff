package org.styli.services.order.model.SalesOrder;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.styli.services.order.model.sales.SalesCreditmemoItem;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sales_creditmemo_item_tax")
public class SalesCreditmemoItemTax {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "sales_creditmemo_item_tax_id", insertable = false, nullable = false)
	private Integer salesCreditmemoItemTaxId;

	@Column(name = "tax_country")
	private String taxCountry;

	@Column(name = "tax_type")
	private String taxType;

	@Column(name = "tax_percentage")
	private BigDecimal taxPercentage;

	@Column(name = "tax_amount")
	private BigDecimal taxAmount;
	
	@ManyToOne
	@JoinColumn(name = "sales_creditmemo_item_id", referencedColumnName = "entity_id", nullable = false, insertable = true, updatable = false)
	@JsonIgnore
	private SalesCreditmemoItem salesCreditmemoItem;
}
