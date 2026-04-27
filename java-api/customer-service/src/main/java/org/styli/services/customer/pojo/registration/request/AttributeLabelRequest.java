package org.styli.services.customer.pojo.registration.request;

import java.util.List;

import lombok.Data;

@Data
public class AttributeLabelRequest {

	private String[] attributeCodes;

	private Integer storeId;

	private List<Integer> productIds;

}
