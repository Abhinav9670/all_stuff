package org.styli.services.order.pojo.request.Order;

import java.io.Serializable;


public enum SourceType implements Serializable {

	APP("app"),
	MSITE("msite"),
	WEB("web"),
	ADMIN("admin");

	  public String value;

	  SourceType(String value) {

	    this.value = value;
	  }

	  public String getValue() {

	    return value;
	  }
	}