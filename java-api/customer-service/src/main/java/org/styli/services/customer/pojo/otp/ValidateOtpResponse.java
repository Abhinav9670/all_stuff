package org.styli.services.customer.pojo.otp;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.styli.services.customer.pojo.registration.response.Customer;

import javax.validation.constraints.NotNull;

/**
 * Created on 06-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@AllArgsConstructor(staticName = "of")
public class ValidateOtpResponse {

  @NotNull private Boolean isValid = false;
  private Customer customer;
  private String newJwtToken; // New JWT token with updated email

  public ValidateOtpResponse() {
	  // empty constructor
  }
}
