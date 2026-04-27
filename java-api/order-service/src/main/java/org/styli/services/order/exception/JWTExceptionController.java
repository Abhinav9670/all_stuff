package org.styli.services.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import lombok.Data;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class JWTExceptionController {

	@ExceptionHandler(value = BadRequestException.class)
	@ResponseBody
	public ResponseEntity<Object> exception(BadRequestException ex) {

		ErrorClass error = new ErrorClass();
		error.setStatusMsg(ex.getMessage());
		error.setStatus(false);
		error.setStatusCode("401");
		return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
	}
}

@Data
class ErrorClass {

	private boolean status;

	private String statusCode;

	private String statusMsg;

}