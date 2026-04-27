package org.styli.services.customer.pojo.otp;

import lombok.Data;
import org.styli.services.customer.pojo.registration.response.ErrorType;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class OtpResponseBody<T> {
    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private T response;
    private ErrorType error;
    private String encryptedRsaToken;
    private String encryptedRsaTokenExpiry;
}
