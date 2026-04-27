package org.styli.services.order.pojo.config;

import java.io.Serializable;
import java.util.List;

import org.styli.services.order.pojo.DisabledServices;

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
	private String code;
	private String shukranProgramCode="SHUKRAN";
	private String shukranSourceApplication="STYLISHOP.COM";
	private String shukranEnrollmentCommonCode="LMSTYLI";
	private String shukranTransactionRTPRURL="https://uat-eas.stylifashion.com/api/v1/shukranTransaction";
	private int shukranEnrollmentConceptCode=54;
	private String shukarnEnrollmentStoreCode="LMSTYLI";
	private String shukranItemTypeCode="S";
	private String globalRedisKey="epsilon-bucket:epsilon-token";
	private int maintenanceMode;
	private DisabledServices disabledServices;
	private boolean isWishlistPubSubEnabled;
	private String brazeWishlistToken;
	private String brazeWishlistUrl;
	private String pubsubWishlistSubscriptionId;
	private int pubsubWishlistMessagesCount;
	private int brazeWishlistCount;
	private String baseCurrencyCode;
}