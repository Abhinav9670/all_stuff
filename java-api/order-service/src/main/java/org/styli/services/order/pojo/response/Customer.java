package org.styli.services.order.pojo.response;

import lombok.Data;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Customer {

	private Integer customerId;

	private String jwtToken;

	private Integer groupId;

	private String createdAt;

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
	
	private Boolean isReferral;
	
    private String updatedEmail;
	
	private boolean updateEmail;
	
	private Integer jwtFlag;
	
	private Integer isActive;
	
	private boolean isWhatsApp;

}
