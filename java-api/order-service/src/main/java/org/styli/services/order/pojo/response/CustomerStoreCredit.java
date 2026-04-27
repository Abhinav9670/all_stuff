package org.styli.services.order.pojo.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author umesh kumar mahato <umesh.mahato@landmarkgroup.com>
 */

@Data
public class CustomerStoreCredit {
    private BigDecimal storeCredit;
    private Integer customerId;
    private Integer storeId;
    private String message;


}
