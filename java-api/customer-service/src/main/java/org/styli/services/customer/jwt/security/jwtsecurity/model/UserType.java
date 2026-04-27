package org.styli.services.customer.jwt.security.jwtsecurity.model;

import java.io.Serializable;

public enum UserType implements Serializable {

	GUEST("guest");

	public String value;

	UserType(String value) {

		this.value = value;
	}

	public String getValue() {

		return value;
	}
}
