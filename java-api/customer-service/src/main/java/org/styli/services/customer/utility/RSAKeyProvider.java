package org.styli.services.customer.utility;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

@Slf4j
@Getter
@Component
public class RSAKeyProvider {

    @Value("${rsa.private.key}")
    private String privateKeyString;

    @Value("${rsa.public.key}")
    private String publicKeyString;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            // Clean private key string
            String cleanedPrivateKey = privateKeyString
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] privateKeyBytes = Base64.getDecoder().decode(cleanedPrivateKey);
            PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(privateSpec);

            // Clean public key string
            String cleanedPublicKey = publicKeyString
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");

            byte[] publicKeyBytes = Base64.getDecoder().decode(cleanedPublicKey);
            X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(publicKeyBytes);
            this.publicKey = KeyFactory.getInstance("RSA").generatePublic(publicSpec);

        } catch (Exception e) {
            log.error("Failed to initialize RSA keys: {}", e.getMessage(), e);
            throw new RSAKeyInitializationException("RSA key initialization failed", e);
        }
    }
}
