package org.styli.services.order.db.product.config.firebase;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;


/**
 * Firebase User model
 * @author Chandan Behera
 *
 */
@Getter
@Setter
public class FirebaseUser implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String uid;
	private String name;
	private String email;
	private boolean isEmailVerified;
	private String issuer;
	private String picture;

}
