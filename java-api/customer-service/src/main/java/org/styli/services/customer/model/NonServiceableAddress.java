package org.styli.services.customer.model;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Id;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

import lombok.Getter;
import lombok.Setter;

/**
 * Non Serviceable Address MONGO collection
 */
@Document(collection = "non_serviceable_address")
@Getter
@Setter
public class NonServiceableAddress implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	private String id;

	private String customerId;
	
	private String customerType;
	
	private String area;
	
	private String city;
	
	private String region;
	
	private String country;
	
	private String latitude;
	
	private String longitude;
	
	private String firstName;
	
	private String lastName;
	
	private String addressId;
	
	private String mobileNumber;
	
	private String fax;
	
	private String streetAddress;
	
	private String telephone;
	
	private String defaultAddress;
	
	private String postCode;
	
	private String regionId;
	
	private String landMark;
	
	private String buildingNumber;
	
	private String storeId;
	
	private String email;
	
	private String formattedAddress;

	@DateTimeFormat(iso = ISO.DATE_TIME)
	private Date createdAt;

	@DateTimeFormat(iso = ISO.DATE_TIME)
	private Date updatedAt;

}