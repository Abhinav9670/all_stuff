package org.styli.services.customer.utility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RsaTokenPayload {

    private String email;
    private String mobile;
    private String firstName;
    private String lastName;
    private long expiresAt;
    private String countryCode;
    private Integer storeId;
}
