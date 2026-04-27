package org.styli.services.order.pojo.request;

import lombok.Data;

import java.io.Serializable;

/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
 */
@Data
public class NavikAddressUpdateDTO implements Serializable {

    String awb;

    String deliveryType;

    String area;

    String address;

    String phoneCode;

    String phoneNumber;

    String client;
}
