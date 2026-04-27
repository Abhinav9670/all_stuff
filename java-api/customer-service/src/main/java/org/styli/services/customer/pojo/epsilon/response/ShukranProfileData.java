package org.styli.services.customer.pojo.epsilon.response;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class ShukranProfileData implements Serializable{

	private String profileId;

	private String tierExpiryDate;

	private String tierName;

	private String tierStartDate;

	private String tierCode;

	private String tierActivity;

	private String previousTierName;

	private String previousTierExpiredDate;

	private String tierNudgeFlag;

	private double availablePoints;

	private String cardNumber;

	private double availablePointsCashValue;

	private String qualifyingTranxCount;

	private String lastEvaluateDate;

	private String language;

	private String memberCity;

	private String nationality;

	private String consentstatus;

	private String isLMSLinked;

	private String consentSource;

	private String consentProvided;

	private String reqQualTxnForNxtTier;

	private String retTierQualTxn;
	private Integer shukranQualifingPurchase;
	private String shukranProfileMessage;
	private String shukranProfileDetailsMessage;
	private Integer shukranWelcomeBonous;
	private String action;
	private String nextTier;
}
