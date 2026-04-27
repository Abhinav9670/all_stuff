package org.styli.services.customer.pojo.account;

import java.io.Serializable;

import lombok.Data;

@Data
public class StyliCoinsData implements Serializable{

	private static final long serialVersionUID = 4235033258033098483L;
	private StyliCoinsCustomerInfo customerInfo;
}
