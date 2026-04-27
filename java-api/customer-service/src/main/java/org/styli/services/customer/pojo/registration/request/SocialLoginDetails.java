package org.styli.services.customer.pojo.registration.request;

import lombok.Data;

@Data
public class SocialLoginDetails {

	private String tokenId;

	private ClientType clientType;

}
