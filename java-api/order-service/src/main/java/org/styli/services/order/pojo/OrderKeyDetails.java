package org.styli.services.order.pojo;

import java.sql.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderKeyDetails {

	@JsonProperty("OMS_INVENTORY_SERVICE_BASE_URL")
	private String inventoryBaseUrl;
	
	@JsonProperty("OMS_SERVICE_BASE_URL")
	private String omsServiceBaseUrl;

	@JsonProperty("OTS_SERVICE_BASE_URL")
	private String otsServiceBaseUrl;

	@JsonProperty("BRAZE_SERVICE_BASE_URL")
	private String brazeServiceBaseUrl;

	@JsonProperty("BRAZE_ATTRIBUTE_PUSH_START_OFFSET")
	private long brazeAttributePushStartOffset;

	@JsonProperty("BRAZE_ATTRIBUTE_PUSH_LIMIT")
	private long brazeAttributePushLimit;
	
	@JsonProperty("OMS_SERVICE_PUBLIC_BASE_URL")
	private String omsServicePublicBaseUrl;
	
	@JsonProperty("WMS_SERVICE_BASE_URL")
	private String wmsUrl;

	@JsonProperty("INCREFF_CREATE_RETURN_ORDER_URL")
	private String increffCreateReturnOrderUrl;

	@JsonProperty("INCREFF_API_KEY")
	private String increffApiKey;


    @JsonProperty("ENABLE_KSA_ADDRESS_SUPPORT")
    private Boolean enableKSAAddressSupport;
	
	@JsonProperty("NAVIK_BASE_URL")
	private String navikBase;
	
	@JsonProperty("ORDER_CANCEL_AMOUNT_REFUND_STYLICREDIT")
	private Integer cancelReturnToStyliCredit;

	@JsonProperty("STYLI_CREDIT_BULK_UPDATE_AMOUNT")
	private String styliCreditBulkUpdateAmount;

	@JsonProperty("STYLI_CREDIT_OMS_UPDATE_AMOUNT")
	private String styliCreditOmsUpdateAmount;
	
	@JsonProperty("REFERRAL_ORDER_SEARCH_IN_LAST_HOURS")
	private Integer referralOrderLastHours;
	
	@JsonProperty("ALPHA_BASE_URL")
	private String alphaBaseUrl;

	@JsonProperty("BETA_BASE_URL")
	private String betaBaseUrl;

	@JsonProperty("CUSTOMER_SERVICE_BASE_URL")
	private String customerServiceBaseUrl;
	
	@JsonProperty("ORDER_REPLICA_RELEASE_COUPON")
	private boolean orderReplicaReleaseCoupon;
	
	@JsonProperty("PENDING_ORDER_EXPIRE_TIME_IN_MINUTE")
	private Integer pendingOrderExpireTimeInMinute;
	
	@JsonProperty("PENDING_ORDER_PICK_FOR_FAILED_MINUTE")
	private int pendingPaymentPickForFailedMinutes;
	
	@JsonProperty("PENDING_ORDER_CHECK_FOR_NOTIFICATION_MINUTE")
	private Integer pendingOrderNotificationInMins;
	
	@JsonProperty("PENDING_ORDER_THRESHOLD_PER_USER")
	private Integer maximumOrderPedningOrderThreshold;

	@JsonProperty("PENDING_ORDER_NOTIFICATION")
	private PendingOrderNotfcnDetails pendingOrderNotfcnDetails;
	
	@JsonProperty("NON_HOLD_ORDERS_PENDING_PAYMENT_SINCE_IN_MINUTE")
	private Integer nonHoldOrdersPendingSinceInMinute;
	
	@JsonProperty("PAYMENT_SERVICE_BASE_URL")
	private String paymentServiceBaseUrl;

	@JsonProperty("order_amount_restriction")
	private OrderRestrictionDetails OrderRestrictionDetails;
	
	@JsonProperty("EMAIL")
	private String email;
	
	@JsonProperty("RETCANPUSHTOWMS")
	private boolean retCanPushToWms;
	
	@JsonProperty("RETURN_FLAG_FIX_DATE")
	private Date returnFlagFixDate;
	
	@JsonProperty("HAWKLIVE_URL")
	private String hawkLiveUrl;
	
	@JsonProperty("AWB_ENCRYPTION_SECRET")
	private String secretkey;
	
	@JsonProperty("AWB_ENCRYPTION_SALT")
	private String salt;

	@JsonProperty("ESTIMATED_DELIVERY_DATE_DAYS_TO_ADD")
	private Integer estimatedDeliveryDateDaysToAdd;

	@JsonProperty("ORDER_TRACKING_BASE_URL")
	private String orderTrackingBaseUrl;

}
