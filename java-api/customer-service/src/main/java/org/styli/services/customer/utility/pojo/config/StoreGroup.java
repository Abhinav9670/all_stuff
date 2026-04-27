package org.styli.services.customer.utility.pojo.config;

import java.io.Serializable;

import lombok.Data;

@Data
public class StoreGroup implements Serializable {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private Integer groupId;
    private Integer websiteId;
    private String name;
    private Integer rootCategoryId;
    private Integer defaultStoreId;
    private String code;

}
