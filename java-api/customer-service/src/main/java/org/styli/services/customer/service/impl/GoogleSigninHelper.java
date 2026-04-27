package org.styli.services.customer.service.impl;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.styli.services.customer.pojo.registration.request.CustomerLoginV4Request;
import org.styli.services.customer.pojo.registration.response.Customer;
import org.styli.services.customer.utility.Constants;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

@Component
public class GoogleSigninHelper {

    private static final Log LOGGER = LogFactory.getLog(GoogleSigninHelper.class);

    @Value("${google.siginin.clientid_ios}")
    String googleClientIdIos;

    @Value("${google.siginin.clientid_android}")
    String googleClientIdAndroid;
    
    @Value("${google.siginin.clientid_web}")
    String googleClientIdWeb;

    public boolean validateGoogleSignin(CustomerLoginV4Request customerLoginRequest, String token, Customer savedCustomer) {

        NetHttpTransport transport = new NetHttpTransport();
        JsonFactory jsonFactory = new GsonFactory();
        boolean status = false;
        System.out.println(googleClientIdIos);
        System.out.println(googleClientIdAndroid);
        System.out.println(googleClientIdWeb);
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                // Specify the CLIENT_ID of the app that accesses the backend:
                .setAudience(Arrays.asList(googleClientIdIos, googleClientIdAndroid, googleClientIdWeb))
                // Or, if multiple clients access the backend:
                //.setAudience(Arrays.asList(CLIENT_ID_1, CLIENT_ID_2, CLIENT_ID_3))
                .build();

        // (Receive idTokenString by HTTPS POST)

        GoogleIdToken idToken = null;
        try {
            idToken = verifier.verify(token);
        } catch (GeneralSecurityException e) {

            LOGGER.error("exception occurred during google sign-in:" + e.getMessage());

        } catch (IOException e) {

            LOGGER.error("exception occurred during google sign-in:" + e.getMessage());
        } catch (Exception e) {

            LOGGER.error("exception occured during google signin:" + e.getMessage());
        }
        if (idToken != null) {
            Payload payload = idToken.getPayload();
            
            LOGGER.info("Payload : " + payload);

            // Print user identifier
            String userId = payload.getSubject();
            LOGGER.info("User ID: " + userId);

            // Get profile information from payload
            String email = payload.getEmail();
          
			if (StringUtils.isNotBlank(customerLoginRequest.getFullName())
					&& customerLoginRequest.getFullName().contains(" ")) {

				String stripFullName = customerLoginRequest.getFullName().replaceAll(Constants.CHARACTERFILETR, "");

				String[] nameArr = stripFullName.split(" ");
				savedCustomer.setFirstName(nameArr[0]);
				savedCustomer.setLastName(nameArr[nameArr.length - 1]);

			}else if(StringUtils.isNotBlank(customerLoginRequest.getFullName())) {
            	savedCustomer.setFirstName(customerLoginRequest.getFullName());
			} else if (StringUtils.isNoneEmpty(customerLoginRequest.getUseridentifier())) {

				String[] firstName = customerLoginRequest.getUseridentifier().split("@");

				if (ArrayUtils.isNotEmpty(firstName)
						&& StringUtils.isNotBlank(firstName[0])) {
					savedCustomer.setFirstName(firstName[0]);
				} else {
					savedCustomer.setFirstName("Google User");
				}
				savedCustomer.setLastName(".");
			}else {
            	
            	savedCustomer.setFirstName("Google User");
            	savedCustomer.setLastName(".");
            }
            
            savedCustomer.setEmail(email);
            savedCustomer.setWebsiteId(customerLoginRequest.getWebsiteId()); 
            
            if(StringUtils.isNotEmpty(payload.getEmail())
					
					&& !payload.getEmail().equalsIgnoreCase(customerLoginRequest.getUseridentifier())) {
				
				return false;
			}
//            if(StringUtils.isNotEmpty(aud)
//					
//					&& (!aud.equalsIgnoreCase(googleClientIdAndroid) || !aud.equalsIgnoreCase(googleClientIdIos) 
//							|| !aud.equalsIgnoreCase(googleClientIdWeb))) {
//				
//				return false;
//			}

            status = true;
        } else {
            LOGGER.info("Invalid ID token.");
            status = false;
        }
        return status;
    }
}