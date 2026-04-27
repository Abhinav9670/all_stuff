package org.styli.services.order.model.Customer;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "new_customer_entity")
@Getter
@Setter
public class    CustomerEntity implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
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

	@OneToMany(mappedBy = "customerEntity", fetch = FetchType.LAZY)
	@OrderBy
	private java.util.Set<CustomerAddressEntity> customerAddressEntity = new HashSet<>();

}
