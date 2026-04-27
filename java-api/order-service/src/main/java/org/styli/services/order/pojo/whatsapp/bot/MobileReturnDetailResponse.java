package org.styli.services.order.pojo.whatsapp.bot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * 
 * @author chandanbehera
 *
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MobileReturnDetailResponse {
	
	private String returnDate;
	
	private String returnStatus;
	
	private String countOfpendingDays; // Count of pending days (return date – today)
	
	private Integer failedAttempt;
	
	private String returnLink;
	
	private String shippingCompany;
	
	private String refundPaymentMode;
	
	private String refundAmount;
	
	private String refundStatus;
	
	private String pickupAddress;
	
	private String pickupContact;
	
	private String returnIncrementId;
	
	private String refundDate;
	
	private String paymentRRN;
	
}
