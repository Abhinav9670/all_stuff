package org.styli.services.order.pojo.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Created on 20-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class CatlogProductEntityRaw implements Serializable {

    private Integer entityId;

    private Integer attributeSetId;

    private String typeId;

    private String sku;

    private int hasOptions;

    private Integer requiredOptions;

}
