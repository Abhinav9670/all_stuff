package org.styli.services.customer.pojo.otp;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class SendOtpRequest {

    private String mobileNo;

    private String email;

    @NotNull
    @Min(1)
    private Integer storeId;

    private MessageCode messageCode;

    private Boolean debugMode;

    private String screen;

    private String userIdentifier;
    
    private Integer customerId;

    private Boolean loginRationalisation;

    private Boolean resendCall;

    public String getMessageCodeName() {
        if (messageCode != null)
            return messageCode.name();
        return MessageCode.DEFAULTMSG.name();
    }
}
