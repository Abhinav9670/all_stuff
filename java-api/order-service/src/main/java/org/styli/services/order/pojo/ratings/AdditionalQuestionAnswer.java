package org.styli.services.order.pojo.ratings;

import java.util.List;

import lombok.Data;

@Data
public class AdditionalQuestionAnswer {
	private List<Integer> options;
	
	private String questionId;
}
