package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class RateDeliveryQuestion {

	private String en;
	private String ar;
	private List<String> ratingText;
	private List<String> ratingAr;
}
