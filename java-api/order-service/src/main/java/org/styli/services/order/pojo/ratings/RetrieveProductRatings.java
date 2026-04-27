package org.styli.services.order.pojo.ratings;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class RetrieveProductRatings {

	private String parentSku;
	
	private String childSku;
	
	private String orderId;
	
	private Integer rate;
	
	private String comments;
	
	private String ratingText;
	
	private List<Integer> options;
	
	private String questionId;
	
	private List<RatingQuestions> questions;
	
	private  List<AdditionalQuestions> additionalQuestions;
	
	private Map<String,List<String>> additionalQuestionAnswer;
	
	// private  List<AdditionalQuestionAnswer> additionalQuestionAnswer;
}
