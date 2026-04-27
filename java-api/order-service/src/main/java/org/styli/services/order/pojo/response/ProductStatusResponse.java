package org.styli.services.order.pojo.response;

import java.io.Serializable;

import org.styli.services.order.pojo.ErrorType;

import lombok.Data;

/**
 * Created on 20-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ProductStatusResponse implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5673511080494469556L;

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private ProductStatusResBody response;

	private ErrorType error;

}
