package org.styli.services.order.pojo.request.Order;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderupdateRequest {

	private Integer customerId;

	private Integer orderId;

    private Integer splitOrderId;
	
	private OrderStatus orderStatus;
	
	private Integer storeId;
	
	private Integer orderAddressId;

	private Integer addressId;

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

	private String regionId; // ID of region

	private String area;

	private String landMark;

    private String unitNumber;

    private String shortAddress;

    private String postalCode;

    private Boolean ksaAddressComplaint = null;

	private String buildingNumber; /** merge this value with streetAddress **/
	
	private String comment;

	private String email;

	private BigDecimal latitude;

	private BigDecimal longitude;

	private String formattedAddress;

	private Boolean isMobileVerified;

	private Boolean isSignUpOtpEnabled;

	private String clientVersion;

	private String source;

	private String langCode="en";

	private Boolean isSplitOrder = false;
}
