package org.styli.services.order.pojo.request.Order;

import java.io.Serializable;

public enum OrderStatus implements Serializable {

	CLOSED("closed"),
	PAYMENT_FAILED("payment_failed"),
    CANCELLED("cancelled");


	  public String value;

	  OrderStatus(String value) {

	    this.value = value;
	  }

	  public String getValue() {

	    return value;
	  }
}
