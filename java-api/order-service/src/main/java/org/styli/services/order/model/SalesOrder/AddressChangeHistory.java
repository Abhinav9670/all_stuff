package org.styli.services.order.model.SalesOrder;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author Biswabhusan Pradhan <biswabhusan.pradhan@landmarkgroup.com>
 */
@Entity
@Getter
@Setter
@Table(name = "address_change_history")
public class AddressChangeHistory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entity_id", insertable = false, nullable = false)
    private Integer entityId;

    @Column(name = "address_id")
    private Integer addressId;

    @Column(name = "order_address_id")
    private Integer orderAddressId;

    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "last_updated_date")
    private Date lastUpdatedDate;

    @Column(name = "firstname")
    private String firstname;

    @Column(name = "middlename")
    private String middlename;

    @Column(name = "lastname")
    private String lastname;

    @Column(name = "region_id")
    private String regionId;

    @Column(name = "fax")
    private String fax;

    @Column(name = "region")
    private String region;

    @Column(name = "postcode")
    private String postcode;

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

    @Column(name = "nearest_landmark")
    private String nearestLandmark;

    @Column(name = "area")
    private String area;

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

}
