package org.styli.services.order.db.product.exception;

public class PayfortException extends RuntimeException {

    private static final long serialVersionUID = -7806029002430564887L;

    private String message;

    public PayfortException() {
    }

    public PayfortException(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
    
}