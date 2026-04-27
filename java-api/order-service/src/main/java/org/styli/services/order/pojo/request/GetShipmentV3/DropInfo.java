package org.styli.services.order.pojo.request.GetShipmentV3;

import lombok.Data;

@Data
public class DropInfo {
    public String email;
    public String name;
    public String landmark;
    public String address;
    public String city;
    public String state;
    public String country_code;
    public String postal_code;
    public String phone;
    public String phone_code;
    private String latitude;
    private String longitude;
}
