package org.styli.services.customer.pojo.account;

import java.io.Serializable;

import lombok.Data;

@Data
public class StyliCoinsCustomerInfo implements Serializable{

	private static final long serialVersionUID = 4087611518224523963L;
	private Integer coinAvailable;
}
