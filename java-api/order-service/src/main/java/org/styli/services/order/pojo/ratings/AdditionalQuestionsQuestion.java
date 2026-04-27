package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class AdditionalQuestionsQuestion {
	private String questionId;
    private String en;
    private String ar;
	private String type;
	private boolean validate = false;
    private List<AdditionalQuestionsRatingOptions> ratingOptions; 
}
