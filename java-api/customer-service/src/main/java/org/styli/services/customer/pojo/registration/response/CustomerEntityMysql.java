package org.styli.services.customer.pojo.registration.response;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "new_customer_entity")
@Getter
@Setter
public class  CustomerEntityMysql implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "entity_id")
	private Integer entityId;

	@Column(name = "website_id", columnDefinition = "SMALLINT")
	private Integer websiteId;

	@Column(name = "email")
	private String email;

	@Column(name = "group_id", columnDefinition = "SMALLINT")
	private Integer groupId;

	@Column(name = "store_id", columnDefinition = "SMALLINT")
	private Integer storeId;

	@Column(name = "created_at", nullable = false, updatable = false)

	private Date createdAt;

	@Column(name = "updated_at", nullable = false)
	private Date updatedAt;

	@Column(name = "is_active", columnDefinition = "SMALLINT")
	private Integer isActive;

	@Column(name = "disable_auto_group_change", columnDefinition = "SMALLINT")
	private Integer disaableGroupChange;

	@Column(name = "created_in")
	private String createdIn;

	@Column(name = "firstname")
	private String firstName;

	@Column(name = "middlename")
	private String middleName;

	@Column(name = "lastname")
	private String lastName;

	@Column(name = "dob")
	private Date dob;

	@Column(name = "password_hash")
	private String passwordHash;

	@Column(name = "default_billing")
	private Integer defaultBilling;

	@Column(name = "default_shipping")
	private Integer defaultShipping;

	@Column(name = "gender", columnDefinition = "SMALLINT")
	private Integer gender;

	@Column(name = "phone_number")
	private String phoneNumber;

	@Column(name = "age_group_id", columnDefinition = "SMALLINT")
	private Integer ageGroupId;

	@Column(name = "jwt_token", columnDefinition = "SMALLINT")
	private Integer jwtToken;

	@Column(name = "whatsapp_optn", columnDefinition = "SMALLINT")
	private Integer whatsappOptn;

	@Column(name = "referral_user", columnDefinition = "SMALLINT")
	private Integer referralUser;

	@Column(name = "client_source")
	private String clientSource;
	
	@Column(name = "social_login_type", columnDefinition = "SMALLINT")
	private Integer socilaLoginType; /**1 for google login , 2 for apple login**/

	@Column(name = "signed_up_using", columnDefinition = "SMALLINT")
	private Integer signedUpUsing; /** 0-email, 1-google, 2-apple **/

	@Column(name = "signed_in_now_using", columnDefinition = "SMALLINT")
	private Integer signedInNowUsing; /** 0-email, 1-google, 2-apple **/

	@Column(name = "last_signed_in_timestamp", nullable = false)
	private Date lastSignedInTimestamp;

	@Column(name = "refresh_token")
	private String refreshToken;

	@Column(name = "user_footprint")
	private String userFootprint;


}