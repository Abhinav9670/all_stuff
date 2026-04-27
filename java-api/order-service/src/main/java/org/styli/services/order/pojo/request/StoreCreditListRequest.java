package org.styli.services.order.pojo.request;

import java.util.List;

import com.sun.istack.NotNull;

import lombok.Data;

@Data
public class StoreCreditListRequest {

	private List<Integer> customerIds;
	
	@NotNull
	private Integer storeId=0;
}
