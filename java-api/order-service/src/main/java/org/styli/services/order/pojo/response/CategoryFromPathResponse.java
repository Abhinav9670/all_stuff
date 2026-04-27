package org.styli.services.order.pojo.response;

import lombok.Data;

import java.io.Serializable;

/**
 * Created on 12-Oct-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */
@Data
public class CategoryFromPathResponse implements Serializable {

    private static final long serialVersionUID = 1753789693703766371L;
    private Integer categoryId;
    private String name;
}
