package org.styli.services.customer.pojo.otp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SendOtpResponse {

  Boolean value = true;
  
  OtpBucketObject otpData;
  
  String channel;
  
  public SendOtpResponse() {
	  //empty constructor
  }
  
  public static SendOtpResponse of(Boolean value, OtpBucketObject otpData) {
	  SendOtpResponse response = new SendOtpResponse();
	  response.setValue(value);
	  response.setOtpData(otpData);
	  return response;
  }
}
