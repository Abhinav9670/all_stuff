package org.styli.services.order.pojo.request.GetShipmentV3;

import lombok.Data;

@Data
public class PickupInfo {
    public String landmark;
    public String state;
    public String address;
    public String country_code;
    public String time;
    public String name;
    public String phone;
    public String phone_code;
    public String email;
    public String city;
    public String postal_code;
    private String latitude;
    private String longitude;
}
