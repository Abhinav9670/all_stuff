package org.styli.services.customer.pojo.registration.request;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * Created on 07-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class TokenPasswordRequest {

  @NotEmpty private String token;
  @NotEmpty private String newPassword;
}
