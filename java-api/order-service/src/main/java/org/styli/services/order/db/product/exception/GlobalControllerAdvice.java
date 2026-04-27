package org.styli.services.order.db.product.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

@ControllerAdvice
public class GlobalControllerAdvice {

    @ExceptionHandler(SellerCancellationfortException.class)
    @ResponseBody
    public SellerCancellationfortException  handleSecurityException(SellerCancellationfortException se) {
        SellerCancellationfortException response = new SellerCancellationfortException(se.getMessage(),true);
        return response;
    }

    @ExceptionHandler(WmsException.class)
    @ResponseBody
    public ResponseEntity<Object> handleWmsException(WmsException e) {
        ErrorResponse error = new ErrorResponse();
        error.setStatus(false);
        error.setStatusCode("400");
        error.setStatusMsg("Payment processing error");
        error.setErrorMessage(e.getMessage());
        error.setHasError(true);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseBody
    public ResponseEntity<Object> handleRuntimeException(RuntimeException e) {
        ResponseEntity<Object> orderIdError = checkOrderIdError(e);
        if (orderIdError != null) {
            return orderIdError;
        }
        // For all other runtime errors, return default 500 error
        ErrorResponse error = new ErrorResponse();
        error.setStatus(false);
        error.setStatusCode("500");
        error.setStatusMsg("Internal server error");
        error.setErrorMessage("An unexpected error occurred: " + e.getMessage());
        error.setHasError(true);
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Object> checkOrderIdError(RuntimeException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            JsonMappingException jsonException = extractJsonMappingException(cause);
            if (jsonException != null && isOrderIdRangeError(jsonException)) {
                return createOrderIdErrorResponse();
            }
            cause = cause.getCause();
        }
        return null;
    }
    
    private JsonMappingException extractJsonMappingException(Throwable cause) {
        if (cause instanceof HttpMessageNotReadableException) {
            Throwable nested = cause.getCause();
            if (nested instanceof JsonMappingException) {
                return (JsonMappingException) nested;
            }
        }
        if (cause instanceof JsonMappingException) {
            return (JsonMappingException) cause;
        }
        return null;
    }
    
    private boolean isOrderIdRangeError(JsonMappingException jsonException) {
        String msg = jsonException.getMessage();
        return msg != null && msg.contains("orderId") && (msg.contains("out of range") || msg.contains("Numeric value"));
    }
    
    private ResponseEntity<Object> createOrderIdErrorResponse() {
        ErrorResponse error = new ErrorResponse();
        error.setStatus(false);
        error.setStatusCode("200");
        error.setStatusMsg("Order ID is not valid or exceeds the maximum allowed range");
        error.setErrorMessage("The provided order ID is too large. Please provide a valid order ID.");
        error.setHasError(true);
        return new ResponseEntity<>(error, HttpStatus.OK);
    }
}

class ErrorResponse {
    private boolean status;
    private String statusCode;
    private String statusMsg;
    private String errorMessage;
    private boolean hasError;

    // Getters and setters
    public boolean isStatus() { return status; }
    public void setStatus(boolean status) { this.status = status; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getStatusMsg() { return statusMsg; }
    public void setStatusMsg(String statusMsg) { this.statusMsg = statusMsg; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isHasError() { return hasError; }
    public void setHasError(boolean hasError) { this.hasError = hasError; }
}