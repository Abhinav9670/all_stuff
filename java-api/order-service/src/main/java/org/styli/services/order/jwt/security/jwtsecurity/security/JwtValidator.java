package org.styli.services.order.jwt.security.jwtsecurity.security;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.order.jwt.security.jwtsecurity.model.JwtUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@Component
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
            if(null != body.get("refreshtoken")) {
             	jwtUser.setRefreshToken((boolean)body.get("refreshtoken"));
             }
            
        } catch (Exception e) {
        	//LOGGER.error("exception occoured during old JWT validation:"+e.getMessage());
        	
        	try {
        	Claims body = Jwts.parser().setSigningKey(jwtsaltNewSecret).parseClaimsJws(token).getBody();

            jwtUser = new JwtUser();

            jwtUser.setUserId(body.getSubject());
            jwtUser.setCode((String) body.get("code"));
            jwtUser.setRole((String) body.get("role"));
            jwtUser.setCustomerId((Integer) body.get("customerId"));
            if(null != body.get("refreshtoken")) {
             	jwtUser.setRefreshToken((boolean)body.get("refreshtoken"));
             }
            
        	}  catch (Exception secondEx) {
        		
                LOGGER.error("exception occoured during JWT validation:"+secondEx.getMessage());
            	
            	jwtUser = new JwtUser();
            	jwtUser.setJwtError(secondEx.getMessage());
            	jwtUser.setErrorFlag(true);
            	
        	}
        }

        return jwtUser;
    }

}
