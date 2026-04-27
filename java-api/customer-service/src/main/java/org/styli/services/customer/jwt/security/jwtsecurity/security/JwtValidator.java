package org.styli.services.customer.jwt.security.jwtsecurity.security;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.customer.utility.Constants;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Scope("singleton")
public class JwtValidator {
	
	
	@Value("${jwt.salt.old.secret}")
	private String jwtSaltOldSecret;
	
	@Value("${jwt.salt.new.secret}")
	private String jwtsaltNewSecret;

	private static final Log LOGGER = LogFactory.getLog(JwtValidator.class);
	

    public JwtUser validate(String token) {

        JwtUser jwtUser = null;
        try {
            Claims body = Jwts.parser().setSigningKey(jwtSaltOldSecret).parseClaimsJws(token).getBody();

            jwtUser = new JwtUser();

            jwtUser.setUserId(body.getSubject());
            jwtUser.setCode((String) body.get("code"));
            jwtUser.setRole((String) body.get("role"));
            jwtUser.setCustomerId((Integer) body.get("customerId"));
            jwtUser.setIsOldToken(true);
            jwtUser.setExpiry(body.getExpiration());
            if(null != body.get("refreshtoken")) {
            	jwtUser.setRefreshToken((boolean)body.get("refreshtoken"));
            }
            
            
        } catch (Exception e) {
        	
        	try {
        	Claims body = Jwts.parser().setSigningKey(jwtsaltNewSecret).parseClaimsJws(token).getBody();
        	
        	
        		
        		 jwtUser = new JwtUser();
                 jwtUser.setUserId(body.getSubject());
                 jwtUser.setCode((String) body.get("code"));
                 jwtUser.setRole((String) body.get("role"));
                 jwtUser.setCustomerId((Integer) body.get("customerId"));
                 jwtUser.setExpiry(body.getExpiration());
                 if(null != body.get("refreshtoken")) {
                 	jwtUser.setRefreshToken((boolean)body.get("refreshtoken"));
                 }
        		
        	
           
            
        	}  catch (Exception secondEx) {
        		
            	LOGGER.info("exception occoured during JWT validation:"+secondEx.getMessage());
            	
            	jwtUser = new JwtUser();
            	jwtUser.setJwtError(secondEx.getMessage());
            	jwtUser.setErrorFlag(true);
            	
        	}
        }

        return jwtUser;
    }
}
