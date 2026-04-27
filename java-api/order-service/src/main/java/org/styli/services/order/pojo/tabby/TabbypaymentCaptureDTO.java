/**
 * 
 */
package org.styli.services.order.pojo.tabby;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 
 * Tabby capture payment payload
 * @author manineemahapatra
 *
 */
@Data
public class TabbypaymentCaptureDTO {
	
	private String id;
	private String amount;
	private String tax_amount;
	private String shipping_amount;
	private String discount_amount;
	private String created_at;
	private List<TabbyItems> items = new ArrayList<>();
	
}
