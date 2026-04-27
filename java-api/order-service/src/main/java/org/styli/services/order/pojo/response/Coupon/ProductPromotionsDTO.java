package org.styli.services.order.pojo.response.Coupon;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

import org.styli.services.order.pojo.ErrorType;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class ProductPromotionsDTO implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -5671511087494469556L;
    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private List<ProductPromotions> response;
    private ErrorType error;
}
