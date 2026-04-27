package org.styli.services.customer.utility.pojo.config;

import java.io.Serializable;
import java.util.List;

import org.json.JSONObject;
import org.styli.services.customer.pojo.DisabledServices;

import lombok.Data;


@Data
public class StoreConfigResponse implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6358837435251272562L;
	private String dbVersion;
	private List<Environments> environments;
	private List<AppEnvironments> appEnvironments;
	private String remoteAddr;
	private String xForwardedFor;
	private int maintenanceMode;
	private String code;
	private boolean quoteRoundOff;
	private boolean styliCoin;
	private Boolean isWishlistPubSubEnabled;
	private String brazeWishlistToken;
	private String brazeWishlistUrl;
	private String pubsubWishlistSubscriptionId;
	private String kafkaWishlistTopicName;
	private int pubsubWishlistMessagesCount;
	private int brazeWishlistCount;
	private DisabledServices disabledServices;
	private int pubsubWishlistNoOfDays;
	private String shukranEnrollmentConceptCode;
	private String shukranEnrollmentCommonCode;
	private String shukarnEnrollmentStoreCode;
	private String shukranEnrollChannelCode;
	private String shukranSource;
	private ShukranQualifingPurchase shukranQualifingPurchase;
	private ShukranNextTier shukranNextTier;
	private String globalRedisKey;
	private String shukranProgramCode;
	private String shukranSourceApplication;
	private List<ShukranCountryCodeMapper> shukranCountryCodeMapper;
}