package org.styli.services.customer.utility.pojo.config;

import java.io.Serializable;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Store implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private Integer storeId;
    private String code;
    private Integer webSiteId;
    private String groupId;
    private String name;
    private Integer sortOrder;
    private Integer isActive;
    private String warehouseLocationCode;
    private String warehouseInventoryTable;
    private String currency;
    private BigDecimal currencyConversionRate;
    private Integer isExternal;
    

}
