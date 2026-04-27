package org.styli.services.customer.exception;

import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import lombok.Data;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.stream.Collectors;

@ControllerAdvice
public class JWTExceptionController {

	private static final Log LOGGER = LogFactory.getLog(JWTExceptionController.class);

	@ExceptionHandler(value = BadRequestException.class)
	@ResponseBody
	public ResponseEntity<Object> exception(BadRequestException ex) {

		ErrorClass error = new ErrorClass();
		error.setStatusMsg(ex.getMessage());
		error.setStatus(false);
		error.setStatusCode("401");
		return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
	@ResponseBody
	public ResponseEntity<Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
		// Silently handle 405 Method Not Allowed errors (typically from bots crawling API endpoints)
		// Return 405 without logging to reduce noise in error logs
		if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
			LOGGER.info("[enableCustomerServiceErrorHandling] HttpRequestMethodNotSupportedException handled: Method Not Allowed");
		}
		ErrorClass error = new ErrorClass();
		error.setStatusMsg("Method Not Allowed");
		error.setStatus(false);
		error.setStatusCode("405");
		return new ResponseEntity<>(error, HttpStatus.METHOD_NOT_ALLOWED);
	}

	@ExceptionHandler(value = ClientAbortException.class)
	@ResponseBody
	public ResponseEntity<Object> handleClientAbortException(ClientAbortException ex) {
		// Handle "Broken pipe" errors - occurs when client disconnects before response is sent
		// This is not a real error, just the client closing the connection
		// Log at INFO level instead of ERROR to reduce noise
		if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
			LOGGER.info("[enableCustomerServiceErrorHandling] Client disconnected before response was sent (Broken pipe): " + ex.getMessage());
		}
		// Return empty response - client has already disconnected
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@ExceptionHandler(value = IOException.class)
	@ResponseBody
	public ResponseEntity<Object> handleIOException(IOException ex) {
		// Handle "Broken pipe" IOException - occurs when client disconnects during response writing
		// This is not a real error, just the client closing the connection
		// Suppress logging to prevent noise in error logs
		if (ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
			if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
				LOGGER.info("[enableCustomerServiceErrorHandling] Client disconnected during response writing (Broken pipe). Suppressing error log.");
			}
			// Return empty response - client has already disconnected
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);
		}
		// For other IOExceptions, wrap in UncheckedIOException and re-throw to let default handling take over
		throw new UncheckedIOException(ex);
	}

	@ExceptionHandler(value = MissingServletRequestPartException.class)
	@ResponseBody
	public ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex) {
		String partName = ex.getRequestPartName();
		ErrorClass error = new ErrorClass();
		error.setStatusMsg("Missing or invalid request part: '" + partName + "'. For document validate use multipart/form-data with keys: image (file), storeId, documentIdType.");
		error.setStatus(false);
		error.setStatusCode("400");
		return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(value = MethodArgumentNotValidException.class)
	@ResponseBody
	public ResponseEntity<Object> handleValidationException(MethodArgumentNotValidException ex) {
		// Extract validation error messages with field names
		String errorMessage = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.collect(Collectors.joining(", "));
		
		// If no specific message, create a generic one
		if (errorMessage.isEmpty()) {
			errorMessage = "Validation failed for request parameters";
		}
		
		ErrorClass error = new ErrorClass();
		error.setStatusMsg(errorMessage);
		error.setStatus(false);
		error.setStatusCode("400");
		return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(value = ResourceAccessException.class)
	@ResponseBody
	public ResponseEntity<Object> handleResourceAccessException(ResourceAccessException ex) {
		// Handle ResourceAccessException (wraps NoHttpResponseException, SocketTimeoutException, etc.)
		// Suppress error logging when flag is enabled to prevent noise in error logs
		// These exceptions are already handled in ExternalServiceAdapterImpl, so we suppress duplicate logging
		if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
			// Return error response without logging to prevent Spring from logging it as an error
			ErrorClass error = new ErrorClass();
			error.setStatusMsg("Service temporarily unavailable");
			error.setStatus(false);
			error.setStatusCode("500");
			return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
		}
		// If flag is disabled, let default handling take over (will log as error)
		throw ex;
	}

	@ExceptionHandler(value = DataIntegrityViolationException.class)
	@ResponseBody
	public ResponseEntity<Object> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
		// Handle DataIntegrityViolationException (includes foreign key constraint violations)
		// Suppress error logging for foreign key constraint violations when flag is enabled
		if (ServiceConfigs.enableCustomerServiceErrorHandling()) {
			Throwable rootCause = ex.getRootCause();
			if (rootCause instanceof SQLIntegrityConstraintViolationException) {
				// Foreign key constraint violation - suppress error logging
				// This is already handled in AccountHelper, but may propagate to controller level
				// Return success response since this is expected behavior (customer may be deleted or event already exists)
				ErrorClass error = new ErrorClass();
				error.setStatusMsg("Database constraint violation");
				error.setStatus(false);
				error.setStatusCode("409");
				return new ResponseEntity<>(error, HttpStatus.CONFLICT);
			}
			// For other data integrity violations, log at INFO level
			LOGGER.info("[enableCustomerServiceErrorHandling] Data integrity violation: " + (rootCause != null ? rootCause.getMessage() : ex.getMessage()));
			ErrorClass error = new ErrorClass();
			error.setStatusMsg("Data integrity violation");
			error.setStatus(false);
			error.setStatusCode("400");
			return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
		}
		// If flag is disabled, let default handling take over (will log as error)
		throw ex;
	}
}

@Data
class ErrorClass {

	private boolean status;

	private String statusCode;

	private String statusMsg;

}