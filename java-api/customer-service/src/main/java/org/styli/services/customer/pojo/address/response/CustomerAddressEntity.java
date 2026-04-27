package org.styli.services.customer.pojo.address.response;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.math.BigDecimal;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.styli.services.customer.model.AuditModel;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_address_entity")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class CustomerAddressEntity extends AuditModel implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "entity_id")
	private Integer entityId;

	@Column(name = "increment_id")
	private String incrementId;

	@Column(name = "parent_id")
	private Integer parentId;

	@Column(name = "created_at")
	// @Column(nullable = false, updatable = false)
	@Temporal(TemporalType.TIMESTAMP)
	@CreatedDate
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Date createdAt;

	// @Column(name="updated_at")
	@Column(name = "updated_at")
	@Temporal(TemporalType.TIMESTAMP)
	@LastModifiedDate
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Date updatedAt;

	@Column(name = "is_active", columnDefinition = "SMALLINT")
	private Integer isActive;

	@Column(name = "city")
	private String city;

	@Column(name = "company")
	private String company;

	@Column(name = "country_id")
	private String countryId;

	@Column(name = "fax")
	private String fax;

	@Column(name = "firstname")
	private String firstname;

	@Column(name = "middlename")
	private String middleName;

	@Column(name = "lastname")
	private String lastName;

	@Column(name = "postcode")
	private String postcode;

	@Column(name = "prefix")
	private String prefix;

	@Column(name = "region")
	private String region;

	@Column(name = "region_id")
	private String regionId;

	@Column(name = "street", length = 65535, columnDefinition = "text")
	private String street;

	@Column(name = "suffix")
	private String suffix;

	@Column(name = "telephone")
	private String telephone;

	@Column(name = "vat_id")
	private String vatId;

	@Column(name = "vat_is_valid")
	private Integer vatIsValid;

	@Column(name = "vat_request_date")
	private String gender;

	@Column(name = "vat_request_id")
	private String vatReqId;

	@Column(name = "vat_request_success")
	private Integer vatReqSuccess;
	
	@Column(name = "formatted_address")
	private String formattedAddress;
	
	@Column(name = "latitude")
	private BigDecimal latitude;
	
	@Column(name = "longitude")
	private BigDecimal longitude;
		
	@Column(name = "is_mobile_verified", columnDefinition = "SMALLINT")
	private Integer isMobileVerified;

	@Column(name = "unit_number")
	private String unitNumber;

	@Column(name = "postal_code")
	private String postalCode;

	@Column(name = "short_address")
	private String shortAddress;

	@Column(name = "national_id_type")
	private String nationalIdType;

	@Column(name = "national_id_image_data", length = 2048)
	private String nationalIdImageData;

	@Column(name = "national_id_number")
	private String nationalIdNumber;

	@Column(name = "national_id_expiration_date")
	private String nationalIdExpirationDate;

	@OneToMany(mappedBy = "customerAddressEntity", fetch = FetchType.LAZY)
	@OrderBy
	private java.util.Set<CustomerAddressEntityVarchar> customerAddressEntityVarchar = new HashSet<>();

}
