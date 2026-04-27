package org.styli.services.order.pojo.response;

import org.styli.services.order.pojo.request.Order.OrderStatus;

import lombok.Data;

@Data
public class UpdateOrderResponse {

	
	private Integer orderId;
	
	private OrderStatus orderStatus;
	
	private Integer storeId;
	
	private Integer orderAddressId;

	private Integer customerAddressId;

	private String firstName;

	private String lastName;

	private String mobileNumber;

	private String city;

	private String fax;

	private String streetAddress;

	private String telephone;

	private String country; // two character = SA

	private String region; // Madinah

	private boolean defaultAddress;

	private String postCode;

    private String unitNumber;

    private String shortAddress;

    private String postalCode;

    private Boolean ksaAddressComplaint = null;

	private String regionId; // ID of region

	private String area;

	private String landMark;

	private String buildingNumber; /** merge this value with streetAddress **/
}
