package org.styli.services.order.pojo.sellercentral;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
public class SellerCentralOrderAddress {


    private String sellerOrderId;
    private String regionId;
    private String fax;
    private String firstName;
    private String lastName;
    private String middleName;
    private String addressType;
    private String area;
    private String formattedAddress;
    private String countryId;
    private String postcode;
    private String city;
    private String street;
    private String telephone;
    private String line1;
    private String name;
    private String state;
    private String email;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String unitNumber;
    private String shortAddress;
    private String postalCode;
    private Boolean ksaAddressComplaint = null;
}
