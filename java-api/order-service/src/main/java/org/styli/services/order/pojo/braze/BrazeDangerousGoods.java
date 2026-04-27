package org.styli.services.order.pojo.braze;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Consolidated class for Braze Dangerous Goods event structures
 */
public class BrazeDangerousGoods {

	@Data
	public static class Event {
		@JsonProperty("external_id")
		private String externalId;

		private String name;
		private String time;
		private EventProperties properties;
	}

	@Data
	public static class EventProperties {
		@JsonProperty("packed")
		private Boolean packed = true;

		@JsonProperty("shipment_id")
		private String shipmentId;

		@JsonProperty("order_id")
		private String orderId;

		@JsonProperty("sku_name")
		private String skuName;

		@JsonProperty("dangerous_goods_flag")
		private Boolean dangerousGoodsFlag;

		@JsonProperty("estimated_delivery_date")
		private String estimatedDeliveryDate;

		@JsonProperty("tracking_link")
		private String trackingLink;

		@JsonProperty("customer_name")
		private String customerName;

		@JsonProperty("store_id")
		private Integer storeId;
	}

	@Data
	public static class RequestBody {
		private List<Event> events;
	}
}
