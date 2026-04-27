package org.styli.services.order.pojo.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * Created on 16-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class ProductDetailsForPDPRequest implements Serializable {

    private static final long serialVersionUID = -2218148525399542186L;

    private List<Integer> productIds;

    @NotNull
    private Integer storeId;

    private Boolean isBagRequest = false;
}
