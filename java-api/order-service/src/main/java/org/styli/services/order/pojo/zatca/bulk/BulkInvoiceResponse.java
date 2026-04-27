package org.styli.services.order.pojo.zatca.bulk;

import java.util.List;

import org.styli.services.order.pojo.zatca.ZatcaErrorList;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class BulkInvoiceResponse {

	@JsonProperty("ErrorList")
	private List<ZatcaErrorList> errorList;
	
	@JsonProperty("einvoicesList")
	private List<EInvoicesListRes> einvoicesList;
}
