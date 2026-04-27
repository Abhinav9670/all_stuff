package org.styli.services.customer.pojo.registration.response;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.styli.services.customer.pojo.FirstFreeShipping;
import org.styli.services.customer.pojo.PreferredPaymentMethod;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import org.styli.services.customer.pojo.epsilon.response.ShukranProfileData;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Customer {

    private Integer customerId;

    private String jwtToken;

    private Integer groupId;

    private String createdAt;
    
    private String otpCreatedAt;

    private String updatedAt;

    private String createdIn;

    private String email;

    private String firstName;

    private String lastName;

    private String mobileNumber;

    private Integer gender;

    private Integer ageGroupId;

    private Date dob;

    private Boolean whatsAppoptn;

    private Boolean customerBlocked = false;

    private Boolean isReferral;

    private Integer genderId;

    private String customerSince;
    private Integer websiteId;

    private Integer signedInNowUsing;
    
    private boolean isPasswordAvailable;

    private String updatedEmail;

    private boolean updateEmail;
    
    private String signUpBy;
	
	private String currentSignInBy;
	
	private Date lastSignedInTimestamp;
	
	private boolean deleteRequested;
	
	private Integer storeId;
	
	private String dobString;
	
	private Integer isActive;
	
	private Integer jwtFlag;

	private Boolean needAlternateEmail;
	
	private FirstFreeShipping firstFreeShipping;
  
	private Boolean isVerifyMobileNumber;
	
	private boolean isWhatsApp;
		
    private Boolean isMobileVerified;
	
	private Boolean isEmailVerified;

    private boolean shukranLinkFlag;

    private String profileId;

    private String cardNumber;

    private String tierActivity;

    private String tierName;
    
    private List<PreferredPaymentMethod> preferredPaymentMethod;

    private Boolean isMobileNumberRemoved;

    private Integer shukranWelcomeBonous;
    private ShukranProfileData shukranProfileData;
	private String refreshToken;
    private Set<LoginHistory> loginHistories = new HashSet<>();

    private Boolean isInfluencer;

    private Boolean mobileNumberUpdateMessageAcknowledged;

    private Boolean isMobileNumberUpdated;
}
