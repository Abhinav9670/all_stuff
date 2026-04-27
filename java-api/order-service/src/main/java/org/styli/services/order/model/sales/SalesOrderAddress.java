package org.styli.services.order.model.sales;

import java.io.Serializable;
import java.math.BigDecimal;

import javax.persistence.*;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Sales Flat Order Address
 */
@Entity
@Table(name = "sales_order_address")
@Data
public class SalesOrderAddress implements Serializable {
  private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "entity_id", insertable = false, nullable = false)
  private Integer entityId;

//  @Column(name = "parent_id")
//  private Integer parentId;

  @Column(name = "customer_address_id")
  private Integer customerAddressId;

  @Column(name = "quote_address_id")
  private Integer quoteAddressId;

  @Column(name = "region_id")
  private String regionId;

  @Column(name = "customer_id")
  private Integer customerId;

  @Column(name = "fax")
  private String fax;

  @Column(name = "region")
  private String region;

  @Column(name = "postcode")
  private String postcode;

  @Column(name = "lastname")
  private String lastname;

  @Column(name = "street")
  private String street;

  // API-3952
  @Column(name = "street_actual")
  private String streetActual;

  @Column(name = "building_number")
  private String buildingNumber;


  @Column(name = "city")
  private String city;

  @Column(name = "email")
  private String email;

  @Column(name = "telephone")
  private String telephone;

  @Column(name = "country_id")
  private String countryId;

  @Column(name = "firstname")
  private String firstname;

  @Column(name = "address_type")
  private String addressType;

  @Column(name = "prefix")
  private String prefix;

  @Column(name = "middlename")
  private String middlename;

  @Column(name = "suffix")
  private String suffix;

  @Column(name = "company")
  private String company;

  @Column(name = "vat_id", columnDefinition = "TEXT")
  private String vatId;

  @Column(name = "vat_is_valid", columnDefinition = "SMALLINT")
  private Integer vatIsValid;

  @Column(name = "vat_request_id", columnDefinition = "TEXT")
  private String vatRequestId;

  @Column(name = "vat_request_date", columnDefinition = "TEXT")
  private String vatRequestDate;

  @Column(name = "vat_request_success", columnDefinition = "SMALLINT")
  private Integer vatRequestSuccess;

  @Column(name = "nearest_landmark")
  private String nearestLandmark;

  @Column(name = "area")
  private String area;
  
  @Column(name = "formatted_address")
  private String formattedAddress;

  @Column(name = "latitude")
  private BigDecimal latitude;
	
  @Column(name = "longitude")
  private BigDecimal longitude;

    @Column(name = "unit_number")
    private String unitNumber;

    @Column(name = "short_address")
    private String shortAddress;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "ksa_address_complaint")
    private Boolean ksaAddressComplaint = null;

  @ManyToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "parent_id", nullable = false, insertable = true, updatable = false)
  @JsonIgnore
  private SalesOrder salesOrder;

}
