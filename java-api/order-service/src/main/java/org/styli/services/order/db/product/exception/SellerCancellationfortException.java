package org.styli.services.order.db.product.exception;

import lombok.ToString;

@ToString
public class SellerCancellationfortException extends RuntimeException {

    private static final long serialVersionUID = -7806029002430564887L;

    private final String errorMessage;
    
    private final boolean hasError;

    public SellerCancellationfortException(String message, boolean hasError) {
        this.errorMessage = message;
        this.hasError = hasError;
    }

   
    
}