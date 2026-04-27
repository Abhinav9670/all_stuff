package org.styli.services.customer.pojo.registration.response;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Maintain the history of login along with device ID and it's refresh token
 */
@Getter
@Setter
@Builder
public class LoginHistory implements Serializable {

	private static final long serialVersionUID = 1L;

	private String deviceId;

	private String refreshToken;
	private LocalDate expiryDate;

	@Builder.Default
	private Date createdAt = new Date();

	@Builder.Default
	private Date updatedAt =  new Date();

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof LoginHistory)) {
			return false;
		}
		LoginHistory other = (LoginHistory) obj;
		return Objects.equals(deviceId, other.deviceId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(deviceId);
	}
}
