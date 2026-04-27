package org.styli.services.order.pojo.response;

import java.util.List;

import lombok.Data;

@Data
public class BankCouponsresponseBody {

	private String id;
	private String couponName;
	private String description;
	private String terms;
	private List<String> termsCond;
	private Integer couponType;
	private List<Integer> cardbinList;
	private String ImageUrl;
	private Integer priority;
	private String percentage;

}
