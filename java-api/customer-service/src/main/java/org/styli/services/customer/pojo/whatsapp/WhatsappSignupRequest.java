package org.styli.services.customer.pojo.whatsapp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Created on 05-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@AllArgsConstructor
@NoArgsConstructor
@Data
public class WhatsappSignupRequest {


    @NotNull
    @NotEmpty
    String mobileNumber;

    String name;

    String userContext;
}
