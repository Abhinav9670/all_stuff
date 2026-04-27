package org.styli.services.order.pojo.autoRefund;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoRefundDTO {

	private List<String> incrementIds;
	private List<String> status;
	private int offset;
	private int pageSize;

}
