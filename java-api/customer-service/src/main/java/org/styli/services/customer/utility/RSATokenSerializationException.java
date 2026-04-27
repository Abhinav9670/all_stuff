package org.styli.services.customer.utility;

public class RSATokenSerializationException extends RSATokenException {
    public RSATokenSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RSATokenSerializationException(String message) {
        super(message);
    }
}
