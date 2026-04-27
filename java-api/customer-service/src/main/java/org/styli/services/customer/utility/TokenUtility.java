package org.styli.services.customer.utility;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.customer.pojo.TtlKeyValue;
import org.styli.services.customer.utility.consul.ServiceConfigs;

import java.util.Date;

/**
 * @author Biswabhusan Pradhan
 * @project customer-service
 */
@Component
public class TokenUtility {

    @Value("${jwt.salt.new.secret}")
    private String jwtSecret;

    TtlKeyValue ttl = ServiceConfigs.getTtls("MAGIC_LINK");

    private final long jwtExpiration = (long) ttl.getValue() * 60000;

    public String createToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();
    }

}
