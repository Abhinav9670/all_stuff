package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class AdditionalQuestionsRatingOptions {
	private String optionId;
	private String en;
	private String ar;
	private List<AdditionalQuestionsQuestion> questions;
}
