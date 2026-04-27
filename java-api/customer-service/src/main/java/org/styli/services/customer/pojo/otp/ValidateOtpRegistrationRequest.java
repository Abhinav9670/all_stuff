package org.styli.services.customer.pojo.otp;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.styli.services.customer.pojo.registration.request.CustomerInfoRequest;

/**
 * Created on 21-OCT-2024
 * 
 * @author Aakanksha<aakanksha.pawan@landmarkgroup.com>
 */

@Data
public class ValidateOtpRegistrationRequest {

    @NotEmpty
    private String userIdentifier;

    @NotEmpty
    private String otp;
    
    private Integer customerId;

    private Integer storeId;
    
    private String screen;
    
    private Boolean autoSavePhone;

}
