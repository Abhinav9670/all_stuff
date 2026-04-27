package org.styli.services.customer.pojo.epsilon.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MemberAddress {
    private String PostalCode;
    private String City;
    private String AddressLine1;
    private String AddressLine2;
    private String LocationCode;
    private AddressJsonExternalData JsonExternalData;
}

