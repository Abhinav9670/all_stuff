package org.styli.services.order.pojo;

import java.io.Serializable;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class ErrorType implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 570896919914258523L;
	private String errorCode;
	private String errorMessage;
}
