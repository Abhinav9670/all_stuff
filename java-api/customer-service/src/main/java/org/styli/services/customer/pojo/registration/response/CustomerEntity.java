package org.styli.services.customer.pojo.registration.response;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.persistence.Id;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

import lombok.Getter;
import lombok.Setter;

import org.styli.services.customer.pojo.PreferredPaymentMethod;
import org.styli.services.customer.pojo.epsilon.response.ShukranProfileData;

import java.util.List;

@Document(collection = "customer_entity")
@Getter
@Setter
public class CustomerEntity implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Id
	private Integer id;

	private Integer entityId;

	private Integer websiteId;

	private String email;

	private Integer groupId;

	private Integer storeId;

	@DateTimeFormat(iso = ISO.DATE_TIME)
	private Date createdAt;

	@DateTimeFormat(iso = ISO.DATE_TIME)
	private Date updatedAt;

	private Integer isActive;

	private Integer disableGroupChange;

	private String createdIn;

	private String firstName;

	private String middleName;

	private String lastName;

	private Date dob;

	private String passwordHash;

	private Integer defaultBilling;

	private Integer defaultShipping;

	private Integer gender;

	private String phoneNumber;

	private String mobileNumber;

	private Integer ageGroupId;

	private Integer jwtToken;

	private Integer whatsappOptn;

	private Integer referralUser;

	private String clientSource;

	private Integer socilaLoginType;
	/** 1 for google login , 2 for apple login, 3-whatsapp **/

	private Integer signedUpUsing;
	/** 0-email, 1-google, 2-apple, 3-whatsapp **/

	private Integer signedInNowUsing;
	/** 0-email, 1-google, 2-apple, 3-whatsapp **/

	private Date LastSignedInTimestamp;

	private String refreshToken;

	private String userFootprint;

	private Boolean isPhoneNumberVerified;
	
	private Set<LoginHistory> loginHistories = new HashSet<>();
	
	private Boolean isUserConsentProvided;

	private Boolean customerBlocked;
	
	private Boolean isMobileVerified;
	
	private Boolean isEmailVerified;

	private Boolean shukranLinkFlag;

	private String profileId;

	private String cardNumber;

	private String tierActivity;

	private String tierName;

	private ShukranProfileData shukranProfileData;
	
	private List<PreferredPaymentMethod> preferredPaymentMethod;

	private Boolean isInfluencer;

	@DateTimeFormat(iso = ISO.DATE_TIME)
	private Date userConsentDate;

	private Boolean isMobileNumberRemoved;

	private Boolean mobileNumberUpdateMessageAcknowledged;

	private Boolean mobileNumberUpdated;

	private Boolean isAddressCompliance; // Flag to track if address is compliant with national address format

	@DateTimeFormat(iso = ISO.DATE_TIME)
	private Date nudgeSeenTime; // Timestamp when user dismissed the address nudge

	public Map<String, Object> toMap() {
		Map<String, Object> map = new HashMap<>();
		map.put("_id", convertToString(this.id));
		map.put("entityId", convertToString(this.entityId));
		map.put("websiteId", convertToString(this.websiteId));
		map.put("email", this.email == null ? this.email : this.email.toLowerCase());
		map.put("storeId", convertToString(this.storeId));
		map.put("created_at", converToDateString(this.createdAt));
		map.put("updated_at", converToDateString(this.updatedAt));
		map.put("isActive", this.isActive);
		map.put("firstName", this.firstName);
		map.put("lastName", this.lastName);
		map.put("phoneNumber", this.phoneNumber);
		map.put("whatsappOptn", convertToString(this.whatsappOptn));
		map.put("referralUser", convertToString(this.referralUser));
		map.put("socialLoginType", convertToString(this.socilaLoginType));
		map.put("signedUpUsing", convertToString(this.signedUpUsing));
		map.put("lastSignedInTimestamp_at", converToDateString(this.LastSignedInTimestamp));
		map.put("shukranLinkFlag",this.shukranLinkFlag);
		map.put("profileId", this.profileId);
		map.put("cardNumber", this.cardNumber);
		return map;
	}
	
	private String convertToString(Object value) {
		return value != null ? value.toString() : null;
	}
	
	private String converToDateString(Date val) {
		if(Objects.isNull(val)) return null;
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.format(val);
	}
}
