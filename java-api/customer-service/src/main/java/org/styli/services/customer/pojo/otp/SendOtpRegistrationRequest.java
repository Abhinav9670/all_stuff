package org.styli.services.customer.pojo.otp;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Created on 18-OCT-2024
 *
 * @author Aakanksha<aakanksha.pawan@landmarkgroup.com>
 */

@Data
public class SendOtpRegistrationRequest {

	@NotEmpty
    private String userIdentifier;

    @NotNull
    @Min(1)
    private Integer storeId;

    private MessageCode messageCode;

    private Boolean debugMode;

    private Integer customerId;

    private String screen;

    private Boolean loginRationalisation;

    private Boolean resendCall;

    public String getMessageCodeName() {
        if(messageCode!=null)
            return messageCode.name();
        return MessageCode.DEFAULTMSG.name();
    }

}
