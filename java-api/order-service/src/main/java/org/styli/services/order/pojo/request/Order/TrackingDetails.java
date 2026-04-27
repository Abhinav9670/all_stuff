package org.styli.services.order.pojo.request.Order;

import lombok.Data;

@Data
public class TrackingDetails {

	private String trackNumber;
	
	private String carrierCode;
	
	private String title;
	
	private String encryptedTrackNumber;
	
	
}
