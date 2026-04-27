package org.styli.services.order.pojo.request.GetShipmentV3.alpha;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Auth {

	private String email;
	
	private String password;
	
	private String token;
	
	private boolean status;
	
	public Auth(String email, String password) {
		super();
		this.email = email;
		this.password = password;
	}
	
}
