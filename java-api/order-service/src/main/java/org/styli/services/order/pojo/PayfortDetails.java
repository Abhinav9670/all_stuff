package org.styli.services.order.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayfortDetails {

	@JsonProperty("PAYFORT_REFUND_BASE_URL")
	private String payfortRefundBaseUrl;

	@JsonProperty("PAYFORT_QUERY_STATUS_CHECK_IN_MINUTE")
	private String payfortQueryStatusCheckInMinute;

	@JsonProperty("PAYFORT_QUERY_FETCH_IN_MINUTE")
	private Integer payfortQueryFetchInMinute;

	@JsonProperty("PAYFORT_QUERY_STATUS_CHECK_BEFORE_HOURES")
	private String payfortQueryStatusCheckbfrhrsAgo;

	@JsonProperty("ksa_credentials")
	private KsaCredentials ksaCredentials;

	@JsonProperty("uae_credentials")
	private UaeCredentials uaeCredentials;

	@JsonProperty("kwt_credentials")
	private KwtCredentials kwtCredentials;

	@JsonProperty("qat_credentials")
	private QatCredentials qatCredentials;

	@JsonProperty("bah_credentials")
	private BahCredentials bahCredentials;

	@JsonProperty("omn_credentials")
	private OmnCredentials omnCredentials;
	
	@JsonProperty("AUTHORIZATION_THRESHOLD_VERSION")
	private String authorizationThresholdVersion;
	
	@JsonProperty("PAYFORT_CAPTURE_CHANGES")
	private boolean payfortCaptureChanges;

	@JsonProperty("SECOND_RETURN_THRESHOLD_VERSION")
	private String secondReturnThresholdVersion;
	
	@JsonProperty("PAYFORT_REFUND_ON_SELLER_CANCELLATION")
	private boolean payfortRefundOnSellerCancellation;

}
