package org.styli.services.customer.model;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * customer_grid_flat
 */
//@Table(name = "customer_grid_flat")
@Data
@Document(collection = "customer_grid_flat")
public class CustomerGridFlat implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Entity ID
   */
  @Id
  private Integer id;
  
  private Integer entityId;

  /**
   * Name
   */
  private String name;

  /**
   * Email
   */
  private String email;

  /**
   * Group_id
   */
  private Integer groupId;

  /**
   * Created_at
   */
  @Temporal(TemporalType.TIMESTAMP)
  @CreatedDate
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private Date createdAt;

  /**
   * Website_id
   */
  private Integer websiteId;

  /**
   * Confirmation
   */
  private String confirmation;

  /**
   * Created_in
   */
  private String createdIn;

  /**
   * Dob
   */
  private Date dob;

  /**
   * Gender
   */
  private Integer gender;

  /**
   * Taxvat
   */
  private String taxvat;

  private String phoneNumber;

  /**
   * Lock_expires
   */
  private Timestamp lockExpires;

  /**
   * Shipping_full
   */
  private String shippingFull;

  /**
   * Billing_full
   */
  private String billingFull;

  /**
   * Billing_firstname
   */
  private String billingFirstname;

  /**
   * Billing_lastname
   */
  private String billingLastname;

  /**
   * Billing_telephone
   */
  private String billingTelephone;

  /**
   * Billing_postcode
   */
  private String billingPostcode;

  /**
   * Billing_country_id
   */
  private String billingCountryId;

  /**
   * Billing_region
   */
  private String billingRegion;

  /**
   * Billing_street
   */
  private String billingStreet;

  /**
   * Billing_city
   */
  private String billingCity;

  /**
   * Billing_fax
   */
  private String billingFax;

  /**
   * Billing_vat_id
   */
  private String billingVatId;

  /**
   * Billing_company
   */
  private String billingCompany;

}