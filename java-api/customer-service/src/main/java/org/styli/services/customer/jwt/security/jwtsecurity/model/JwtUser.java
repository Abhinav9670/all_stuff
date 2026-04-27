package org.styli.services.customer.jwt.security.jwtsecurity.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

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
    
    private String jwtError = null;
    
    
    private Boolean errorFlag = false;
     

}
