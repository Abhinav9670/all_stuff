package org.styli.services.order.pojo.ratings;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class UpdateCustomerRatingsReq {
	
	private String orderId;
	private String customerId;
    private String customerEmail;
    private String parentSku;
    private String childSku;
    private String storeId;
    private String rate;
    private String ratingText;
    private String comments;
    private List<Integer> options;
    private String questionId;
    
    private Map<String,List<String>> additionalQuestionAnswer;

}
