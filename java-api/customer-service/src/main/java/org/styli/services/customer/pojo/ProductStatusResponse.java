package org.styli.services.customer.pojo;

import java.io.Serializable;

import org.styli.services.customer.pojo.registration.response.Product.ProductStatusResBody;
import org.styli.services.customer.utility.pojo.ErrorType;

import lombok.Data;

/**
 * Created on 20-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ProductStatusResponse implements Serializable {

    private static final long serialVersionUID = -7494610692914103061L;

    private boolean status;

    private String statusCode;

    private String statusMsg;

    private ProductStatusResBody response;

    private ErrorType error;
}
