package org.styli.services.customer.pojo.eas;

import java.util.Date;
import lombok.Data;

@Data
public class EarnOnProfileUpdateCompleteRequest {
	private Integer customerId;
	private Integer storeId;
	private String firstName;
	private String middleName;
	private String lastName;
	private String email;
	private String mobileNumber;
	private Integer ageGroupId;
	private Integer gender;
	private String dob;
	private Boolean isVerifyMobileNumber;
}
