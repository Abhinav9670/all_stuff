package org.styli.services.customer.pojo.whatsapp;

import lombok.Data;

/**
 * Created on 05-Oct-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class WhatsappSignupResponse {

    private String token;
    private String url;
    private String message;
}
