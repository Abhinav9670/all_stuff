package org.styli.services.order.pojo.response;

import lombok.Data;

import java.util.List;

@Data
public class WarehouseResponseWrapper {

	private boolean status;
	private String statusCode;
	private String statusMsg;
	private String estimated_date;
	private String estimated_date_tostring_en;
	private String estimated_date_tostring_ar;
	private List<WarehouseResponse> response;
}
