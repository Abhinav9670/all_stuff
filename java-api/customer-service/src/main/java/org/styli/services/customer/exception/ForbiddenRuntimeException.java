package org.styli.services.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Created on 12-Jul-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@ResponseStatus(value = HttpStatus.FORBIDDEN)
public class ForbiddenRuntimeException extends RuntimeException {

    public ForbiddenRuntimeException() {
        this("This request is forbidden!");
    }

    public ForbiddenRuntimeException(String message) {
        super(message);
    }
}
