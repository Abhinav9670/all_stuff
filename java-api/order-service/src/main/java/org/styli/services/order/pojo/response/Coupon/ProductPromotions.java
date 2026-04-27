package org.styli.services.order.pojo.response.Coupon;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
@JsonInclude(Include.NON_NULL)
public class ProductPromotions implements Serializable {

    private static final long serialVersionUID = -5673513087494469556L;

    private String id;
    private String name;
    private String description;
    private String terms;
    @JsonProperty("terms_cond")
    private List<String> termsCond;
    private String couponCode;
    private String source;
    private String couponType;

}
