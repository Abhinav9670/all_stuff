package org.styli.services.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import lombok.ToString;

@ResponseStatus(value = HttpStatus.UNAUTHORIZED)
@ToString
public class BadRequestException extends RuntimeException {
  /**
   *
   */
  private static final long serialVersionUID = 1L;
  private final String resourceName;
  private final String fieldName;
  private final transient Object fieldValue;

  public BadRequestException(String resourceName, String fieldName, Object fieldValue) {
    super(String.format("%s not found with %s : '%s'", resourceName, fieldName, fieldValue));
    this.resourceName = resourceName;
    this.fieldName = fieldName;
    this.fieldValue = fieldValue;
  }

}
