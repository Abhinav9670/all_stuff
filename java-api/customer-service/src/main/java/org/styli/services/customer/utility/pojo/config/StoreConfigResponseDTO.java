package org.styli.services.customer.utility.pojo.config;

import java.io.Serializable;

import org.styli.services.customer.utility.pojo.ErrorType;

import lombok.Data;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class StoreConfigResponseDTO implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 5591889150885636913L;
    private Boolean status;
    private String statusCode;
    private String statusMsg;
    private StoreConfigResponse response;
    private ErrorType error;
}
