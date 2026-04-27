package org.styli.services.order.jwt.security.jwtsecurity.model;

import java.util.Date;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JwtUser {
	 private String userId;
	    private String code;
	    private String role;
	    private Integer customerId;
	    private Boolean isOldToken = false;
	    private String jwtFlag;
	    private Date expiry;
	    private String uuid;
	    private Boolean refreshToken = false;
		private String jwtError ;
		private Boolean errorFlag = false;
		

}
