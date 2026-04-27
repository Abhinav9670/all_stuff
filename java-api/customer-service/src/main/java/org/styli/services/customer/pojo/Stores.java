package org.styli.services.customer.pojo;

import java.io.Serializable;

import lombok.Data;

@Data
public class Stores implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -196389317581347746L;
	private String storeId;
	private String storeCode;
	private String storeLanguage;
	private String storeCurrency;
	private int websiteId;
	private String websiteIdentifier;
	private String storeName;
	private String websiteCode;
	private String countryCode;

}
