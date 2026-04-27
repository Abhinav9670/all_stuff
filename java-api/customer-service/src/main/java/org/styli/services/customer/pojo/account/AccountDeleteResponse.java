package org.styli.services.customer.pojo.account;


import lombok.Data;
import org.styli.services.customer.pojo.otp.OtpBucketObject;
import org.styli.services.customer.pojo.registration.response.ErrorType;

/**
 * @author umesh.mahato@landmarkgroup.com
 * @project customer-service
 * @created 10/06/2022 - 12:13 PM
 */

@Data
public class AccountDeleteResponse {
    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private ErrorType error;
    private AccountDeletionEligibleResponse response;
    private OtpBucketObject otpData;
}
