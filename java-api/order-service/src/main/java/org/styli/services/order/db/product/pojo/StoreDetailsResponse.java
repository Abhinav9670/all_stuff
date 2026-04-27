package org.styli.services.order.db.product.pojo;

import java.io.Serializable;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class StoreDetailsResponse implements Serializable{

	private static final long serialVersionUID = 1L;
	
	private int id;
    private String currency;
    private String code;

}
