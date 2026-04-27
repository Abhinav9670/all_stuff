package org.styli.services.customer.utility.pojo.category;

import java.io.Serializable;

import org.styli.services.customer.utility.pojo.ErrorType;

import lombok.Data;

@Data
public class CategoryListResponse implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5591542135836284817L;

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CategoryResponseBody response;

	private ErrorType error;

}
