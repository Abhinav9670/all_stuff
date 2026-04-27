package org.styli.services.customer.pojo.eas;

import java.util.Date;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EarnCustomerUpdateProfileRequest {
	
	@NotNull
    @Min(1)
	private Integer customerId;
	
	@NotNull
    @Min(1)
	private Integer storeId;
	private Integer ageGroupId;
	private Integer gender;
	private String mobileNumber;
	
	@NotNull
    @Min(1)
	private Integer stage;
	
	private Date dob;
	private Boolean isVerifyMobileNumber;
}
