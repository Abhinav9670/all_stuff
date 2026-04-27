package org.styli.services.customer.pojo.epsilon.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString

public class AddressJsonExternalData {
    private String CountryCode;
    private String StateCode;
}

