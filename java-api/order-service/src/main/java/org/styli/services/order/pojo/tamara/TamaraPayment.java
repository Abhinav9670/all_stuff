package org.styli.services.order.pojo.tamara;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class TamaraPayment {
	
	@JsonProperty("order_id")
	private String orderId;
	
	@JsonProperty("order_reference_id")
	private String orderReferenceId;
	
	@JsonProperty("order_number")
	private String orderNumber;
	
	private String description;
	
	private TamaraConsumer consumer;
	
	private String status;
	
	@JsonProperty("payment_type")
	private String paymentType;
	
	private int instalments;
	
	@JsonProperty("created_at")
    private String createdAt;
	
	private Transactions transactions;
	
	@JsonProperty("order_expiry_time")
    private String orderExpiryTime;
	
	@JsonProperty("capture_id")
	private String captureId;
	
	@JsonProperty("total_amount")
	private Amount totalAmount;
	
	private List<TamaraRefunds> refunds = new ArrayList<>();
	
}
