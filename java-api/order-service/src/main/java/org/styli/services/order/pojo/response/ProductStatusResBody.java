package org.styli.services.order.pojo.response;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * Created on 20-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ProductStatusResBody implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5643511087494469556L;

	List<ProductValue> productStatus;

}
