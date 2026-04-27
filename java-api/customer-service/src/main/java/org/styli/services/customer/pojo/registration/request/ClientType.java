package org.styli.services.customer.pojo.registration.request;

import java.io.Serializable;

public enum ClientType implements Serializable {

	IOS_CLIENT("IOS"), ANDROID_CLIENT("ANDROID"), WEB_CLEINT("WEB") , M_SITE_CLIENT("M-SITE"),HUAWEY_CLIENT("HUAWEY");

	public String value;

	ClientType(String value) {

		this.value = value;
	}

	public String getValue() {

		return value;
	}

}
