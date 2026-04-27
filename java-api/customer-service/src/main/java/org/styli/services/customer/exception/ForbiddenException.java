package org.styli.services.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.UnknownHostException;


/**
 * Created on 29-Jun-2021
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@ResponseStatus(value = HttpStatus.FORBIDDEN)
public class ForbiddenException extends IOException {

    public static final HttpStatus HTTP_STATUS = HttpStatus.FORBIDDEN;

    public ForbiddenException() {
        this("This request is forbidden!");
    }

    public ForbiddenException(String message) {
        super(message);
    }
}
