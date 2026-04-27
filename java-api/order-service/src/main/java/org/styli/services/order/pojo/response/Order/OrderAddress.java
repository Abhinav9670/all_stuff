package org.styli.services.order.pojo.response.Order;

import java.math.BigDecimal;

import lombok.Data;

/**
 * @author Umesh, 11/05/2020
 * @project product-service
 */

@Data
public class OrderAddress {

    private Integer addressId;

    private Integer customerAddressId;

    private Boolean isAddressChangeEligible;

    private Boolean isAddressChangeEnabled;

    private Boolean defaultAddress;

    private String firstName;

    private String lastName;

    private String mobileNumber;

    private String city;

    // private String fax;

    private String streetAddress;

    // private String telephone;

    private String country; // two character = SA

    private String region; // Madinah

    private String postCode;

    private String regionId; // ID of region

    private String area;

    private String buildingNumber; // merge this value with streetAddress

    private String landmark;
    
    private BigDecimal latitude;
    
    private BigDecimal longitude;

    private String streetAddressActual;

    private String unitNumber = "";

    private String shortAddress = "";

    private String postalCode = "";

    private Boolean ksaAddressComplaint = null;
}
