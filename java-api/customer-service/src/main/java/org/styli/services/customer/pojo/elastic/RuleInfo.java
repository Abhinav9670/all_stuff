package org.styli.services.customer.pojo.elastic;

import java.io.Serializable;

import lombok.Data;

/**
 * Created on 30-Sep-2020
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
public class RuleInfo implements Serializable{
    private static final long serialVersionUID = 7219861070146263589L;
	private String name;
}
