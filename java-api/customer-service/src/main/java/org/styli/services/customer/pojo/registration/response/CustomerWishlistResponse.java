package org.styli.services.customer.pojo.registration.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

import java.io.Serializable;

@Data
@JsonInclude(Include.NON_NULL)
public class CustomerWishlistResponse implements Serializable {

	private static final long serialVersionUID = -8920382721986770542L;

	private boolean status;

	private String statusCode;

	private String statusMsg;

	private CustomerWishListBody response;

	private ErrorType error;
}
