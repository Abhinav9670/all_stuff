package org.styli.services.order.pojo.zatca.bulk;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ZatcaBulkRequest {

	@JsonProperty("Invoices")
	private List<ZatcaInvoiceBulkSingle> Invoices;
}
