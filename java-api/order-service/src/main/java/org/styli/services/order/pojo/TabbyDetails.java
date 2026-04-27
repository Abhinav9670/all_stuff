package org.styli.services.order.pojo;

import java.sql.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TabbyDetails {

	@JsonProperty("tabby_base_url")
	private String tabbyBaseUrl;

	/** Base URL for Tabby v2 API when store is KSA (e.g. https://api.tabby.sa). When set, used only for v2 endpoints + KSA store. */
	@JsonProperty("tabby_ksa_base_url")
	private String tabbyKsaBaseUrl;
	
	@JsonProperty("orders_minutes_ago")
	private String ordersMinutesAgo;
	
	@JsonProperty("orders_hours_ago")
	private String ordersHoursAgo;
	
	@JsonProperty("tabby_failed_replica_release_call")
	private boolean tabbyReplicaFailedCall;
	
	@JsonProperty("refund_on_replica")
	private boolean refundOnReplica;
	
	@JsonProperty("enable_backend_create_order")
	private boolean enableBackendCreateOrder;
	
	@JsonProperty("backend_create_order_duration_mins")
	private Integer backendCreateOrderDurationMins;

  @JsonProperty("rto_from_date")
	private Date rtoFromDate;
	
}
