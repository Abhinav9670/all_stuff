package org.styli.services.customer.pojo.registration.response;

import org.styli.services.customer.pojo.address.response.CustomerAddrees;

import lombok.Data;
import org.styli.services.customer.pojo.epsilon.response.ShukranProfileData;

@Data
public class CustomerProfileResponse {

	private String userMessage;

	private boolean status;

	private Customer customer;

	private CustomerAddrees defaultAddress;

	private ShukranProfileData shukranProfileData;

	private boolean shukranLinkFlag;

	private Boolean isShukranEnable;

	private boolean shukranProfileExists;

	private boolean shukranAPIFailing;

	private String profileId;

	private Boolean ShowPopup;

	private Boolean showAddressComplianceNudge; // Flag to show nudge for address compliance

	private Boolean ksaAddressCompliant; // true if all active addresses are KSA compliant, false otherwise
}
