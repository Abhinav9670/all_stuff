package org.styli.services.customer.pojo;

import lombok.Data;

@Data
public class EncryptedTokenResponse {

    private String token;
    private String expiry;

}
