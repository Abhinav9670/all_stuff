package org.styli.services.customer.utility.pojo;

import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;

@Getter
@Setter
public class ErrorType implements Serializable{
	private static final long serialVersionUID = 1198159589421612223L;
	private String errorCode;
    private String errorMessage;
}
