package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class RatingQuestions {

	private String ratings;
	private String ratingText;
	private String ratingAr;
	private String questionId;
	private String en;
	private String ar;
	
	private List<RatingOptions> ratingOptions; 
}
