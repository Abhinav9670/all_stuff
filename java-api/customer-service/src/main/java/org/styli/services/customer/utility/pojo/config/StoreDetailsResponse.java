package org.styli.services.customer.utility.pojo.config;

import java.io.Serializable;

import lombok.Data;

@Data
public class StoreDetailsResponse implements Serializable{

    private static final long serialVersionUID = -6544102764699031424L;
	private int id;
    private String currency;
    private String code;

}
