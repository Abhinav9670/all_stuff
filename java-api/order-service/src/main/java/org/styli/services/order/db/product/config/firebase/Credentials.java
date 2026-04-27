package org.styli.services.order.db.product.config.firebase;

import com.google.firebase.auth.FirebaseToken;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Firebase credential modal
 * @author Chandan Behera
 *
 */

@Data
@AllArgsConstructor
public class Credentials {

	public enum CredentialType {
		ID_TOKEN, SESSION
	}

	private CredentialType type;
	private FirebaseToken decodedToken;
	private String idToken;
	private String session;

}