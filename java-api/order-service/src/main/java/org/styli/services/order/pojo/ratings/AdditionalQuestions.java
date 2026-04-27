package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class AdditionalQuestions {

	private String ratings;
    private String ratingText;
    private String ratingAr;
    private List<AdditionalQuestionsQuestion> questions;
    
}
