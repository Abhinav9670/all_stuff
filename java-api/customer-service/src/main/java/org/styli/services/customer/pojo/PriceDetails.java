package org.styli.services.customer.pojo;

import java.io.Serializable;

import lombok.Data;

@Data
public class PriceDetails implements Serializable {

	private static final long serialVersionUID = 164504961562174856L;

	private String price;

	private String specialPrice;

}
