package org.styli.services.order.db.product.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

public class ExceptionControllerAdvice {

	@ExceptionHandler(PayfortException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public PayfortExceptionResponse handleSecurityException(SecurityException se) {
		PayfortExceptionResponse response = new PayfortExceptionResponse(se.getMessage());
        return response;
    }
}
