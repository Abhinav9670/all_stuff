package org.styli.services.customer.pojo.otp;

import lombok.Data;
import org.styli.services.customer.pojo.registration.request.CustomerInfoRequest;

import javax.validation.constraints.NotEmpty;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class ValidateOtpRequest {

  private String mobileNo;
  private String email;
  @NotEmpty private String otp;
  private OtpValidationType type;
  private CustomerInfoRequest customerInfo;
  private Integer customerId;
  private String userIdentifier;
  private Integer storeId;
  private Boolean loginRationalisation;

}
