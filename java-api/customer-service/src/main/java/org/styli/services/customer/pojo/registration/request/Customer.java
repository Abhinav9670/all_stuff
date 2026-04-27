package org.styli.services.customer.pojo.registration.request;

import java.util.List;

import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Customer {

	private String lastName;

	private String firstName;

	@NotBlank
	private String email;

	private String phone;

	private int storeId;

	private int websiteId;

	private Integer gender;

	private Integer ageGroupId;

	private List<Address> addresses;
	
	private String oldEmail;

	private Boolean isUserConsentProvided;

	private Boolean isMobileNumberUpdated;

	private Boolean mobileNumberUpdateMessageAcknowledged;
}
