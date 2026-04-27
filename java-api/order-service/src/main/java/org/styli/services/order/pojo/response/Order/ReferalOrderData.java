package org.styli.services.order.pojo.response.Order;

import lombok.Data;

@Data
public class ReferalOrderData {

	private String customer_id;
	
	private String order_id;
	
	private String order_increment_id;
	
	private String store_id;
	
	private String order_date;
	
	private String delivered_date;
	
	private String grand_total;
	
	private String email_id;
	
	private String meta_fields;
}
