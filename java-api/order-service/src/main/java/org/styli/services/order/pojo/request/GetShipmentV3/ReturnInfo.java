package org.styli.services.order.pojo.request.GetShipmentV3;

import lombok.Data;

import java.util.Date;

@Data
public class ReturnInfo {
    public String landmark;
    public String state;
    public String address;
    public String country_code;
    public Date time;
    public String name;
    public String phone;
    public String phone_code;
    public String email;
    public String city;
    public String postal_code;
}
