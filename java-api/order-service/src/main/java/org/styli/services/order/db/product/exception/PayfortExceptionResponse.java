package org.styli.services.order.db.product.exception;

public class PayfortExceptionResponse {

	private String error;

    public PayfortExceptionResponse() {

    }

    public PayfortExceptionResponse(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
