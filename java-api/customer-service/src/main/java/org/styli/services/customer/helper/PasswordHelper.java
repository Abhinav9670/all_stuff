package org.styli.services.customer.helper;

import com.kosprov.jargon2.api.Jargon2;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.styli.services.customer.jwt.security.jwtsecurity.model.JwtUser;
import org.styli.services.customer.jwt.security.jwtsecurity.security.JwtGenerator;
import org.styli.services.customer.service.impl.CustomerV4ServiceImpl;
import org.styli.services.customer.utility.Constants;

import javax.xml.bind.DatatypeConverter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static com.kosprov.jargon2.api.Jargon2.jargon2Hasher;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Component
@Scope("singleton")
public class PasswordHelper {

        private static final Log LOGGER = LogFactory.getLog(PasswordHelper.class);
        @Autowired
        private JwtGenerator jwtGenerator;

        @Value("${customer.jwt.flag}")
        String jwtFlag;

        /**
         *
         * @param password
         * @param salt
         * @return String
         * @throws NoSuchAlgorithmException Reference:
         *                                  https://stackoverflow.com/questions/3103652/hash-string-via-sha-256-in-java
         */
        public String getSha256Hash(String password, String salt) throws NoSuchAlgorithmException {

                final String DELIMITER = ":";
                final int HASH_VERSION_SHA256 = 1;
                final int DEFAULT_SALT_LENGTH = 32;
                final String HASH_METHOD = "SHA-256";

                // Create salt when registering customer
                if (salt == null)
                        salt = RandomStringUtils.randomAlphanumeric(DEFAULT_SALT_LENGTH);
                String saltedPassword = salt + password;

                MessageDigest md = MessageDigest.getInstance(HASH_METHOD);
                md.update(saltedPassword.getBytes(StandardCharsets.UTF_8));
                byte[] digest = md.digest();
                String hex = String.format("%064x", new BigInteger(1, digest));

                return new StringBuilder(hex).append(DELIMITER).append(salt).append(DELIMITER)
                                .append(HASH_VERSION_SHA256).toString();
        }

        /**
         *
         * @param customerPassword
         * @param customerSalt
         * @return String Reference: https://github.com/kosprov/jargon2-api
         */
        public String getArgon2Id13Hash(String customerPassword, String customerSalt) {
                final String DELIMITER = ":";
                final int HASH_VERSION_SHA256 = 2;

                byte[] password = customerPassword.getBytes();
                byte[] salt = customerSalt.getBytes();

                Jargon2.Type type = Jargon2.Type.ARGON2id;
                int memoryCost = 65536;
                int timeCost = 2;
                int parallelism = 1;
                int hashLength = 32;

                // Configure the hasher
                Jargon2.Hasher hasher = jargon2Hasher().version(Jargon2.Version.V13).type(type).memoryCost(memoryCost)
                                .timeCost(timeCost).parallelism(parallelism).hashLength(hashLength);

                byte[] rawHash = hasher.salt(salt).password(password).rawHash();
                String hex = DatatypeConverter.printHexBinary(rawHash);

                return new StringBuilder(hex).append(DELIMITER).append(customerSalt).append(DELIMITER)
                                .append(HASH_VERSION_SHA256).toString();
        }

        public String generateToken(String userId, String code, Integer customerId , boolean refreshToken) {

                String jwtToken;
                JwtUser jwtUser = new JwtUser();
                LOGGER.info("jwt token expire time "+ Constants.jwtTokenExpireTimeInMinutes());
                Date accessTokenExpiryOn = addXxxToDate(Calendar.MINUTE, Constants.jwtTokenExpireTimeInMinutes()); //TODO
                if(refreshToken) {
                        jwtUser.setExpiry(accessTokenExpiryOn);
                        jwtUser.setRefreshToken(true);
                }
                jwtUser.setUserId(userId);
                jwtUser.setCode(code);
                jwtUser.setRole("user");
                jwtUser.setRefreshToken(refreshToken);
                if("1".equals(jwtFlag)) {
                   jwtUser.setCustomerId(customerId);
                   jwtUser.setJwtFlag(jwtFlag);
                }
                jwtToken = jwtGenerator.generate(jwtUser);

                return jwtToken;
        }
        
	public String generateRefreshToken() {
		return UUID.randomUUID().toString();
	}
    
    /**
     * Accepts Calendar.MONTH, Calendar.MINUTE as unit to add and value to be added to current date and time. 
     * Returns resultant java.util.Date
     * 
     * @param unit
     * @param value
     * @return java.util.Date
     */
	public Date addXxxToDate(int unit, int value) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.add(unit, value);
		return calendar.getTime();
	}

}
