package org.styli.services.customer.db.product.config.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Firebase authentication
 * 
 * @author Chandan Behera
 *
 */
@Component
@Slf4j
public class FirebaseAuthentication {

	/**
	 * Verify firebase token
	 * 
	 * @param request
	 */
	public void verifyToken(HttpServletRequest request) {
		String session = null;
		FirebaseToken decodedToken = null;
		Credentials.CredentialType type = null;
		String token = getBearerToken(request);
		String referer = request.getHeader("referer");
		log.info("Authorization token : {}. Referer : {}", token, referer);
		try {
			if (token != null && !token.equalsIgnoreCase("")) {
				decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
				type = Credentials.CredentialType.ID_TOKEN;
			}
		} catch (FirebaseAuthException e) {
			log.error("Error in Firebase authentication : ", e);
		}
		FirebaseUser user = firebaseTokenToUserDto(decodedToken);
		if (user != null) {
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user,
					new Credentials(type, decodedToken, token, session), null);
			authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authentication);
			log.info("Firebase authentication success for : {}", user.getEmail());
		} else {
			log.info("Firebase authentication failed.");
		}
	}

	private FirebaseUser firebaseTokenToUserDto(FirebaseToken decodedToken) {
		FirebaseUser user = null;
		if (decodedToken != null) {
			user = new FirebaseUser();
			user.setUid(decodedToken.getUid());
			user.setName(decodedToken.getName());
			user.setEmail(decodedToken.getEmail());
			user.setPicture(decodedToken.getPicture());
			user.setIssuer(decodedToken.getIssuer());
			user.setEmailVerified(decodedToken.isEmailVerified());
		}
		return user;
	}

	/**
	 * Extract bearer token from HTTP request header
	 * 
	 * @param request
	 * @return
	 */
	private String getBearerToken(HttpServletRequest request) {
		String bearerToken = null;
		String authorization = request.getHeader("Authorization");
		if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
			bearerToken = authorization.substring(7);
		}
		return bearerToken;
	}

}
