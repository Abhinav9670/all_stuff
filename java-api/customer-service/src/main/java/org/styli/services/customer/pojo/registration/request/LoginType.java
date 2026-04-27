package org.styli.services.customer.pojo.registration.request;

import java.io.Serializable;

public enum LoginType implements Serializable {

	EMAIL_LOGIN("EMAIL"),
	MOBILE_LOGIN("MOBILE"),
	SOCIAL_LOGIN_FB("FBLOGIN"),
	SOCIAL_LOGIN_GOOGLE("GOOGLELOGIN"),
	SOCIAL_LOGIN_APPLE("APPLELOGIN"),
	SOCIAL_LOGIN_WHATSAPP("WHATSAPPLOGIN");

	public String value;

	LoginType(String value) {

		this.value = value;
	}

	public String getValue() {

		return value;
	}

}
