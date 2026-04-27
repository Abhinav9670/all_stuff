package org.styli.services.order.pojo.response.external;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

@Data
public class CustomCouponListResponse implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5852358903012614341L;

	private Integer code;

	private String status;

	private List<CustomCouponData> data;

	private Integer total;

}
